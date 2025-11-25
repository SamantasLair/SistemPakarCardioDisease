package com.praktikumpbo.cardioexpert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.AttributeStats;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

public class InferenceEngine {

    private static Classifier classifier;
    public static String j48Rules = "Model belum dilatih.";

    public static Map<String, Double> means = new HashMap<>();
    public static Map<String, Double> stdDevs = new HashMap<>();
    public static Map<String, Double> weights = new HashMap<>();

    public static void trainSystem(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        loader.setFieldSeparator(";");
        Instances data = loader.getDataSet();
        
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        calculateStatisticsAndWeights(data);
        trainJ48(data);
    }

    private static void calculateStatisticsAndWeights(Instances data) throws Exception {
        CorrelationAttributeEval eval = new CorrelationAttributeEval();
        eval.buildEvaluator(data);

        try (Connection conn = DBConnect.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fuzzy_stats_v2 (attribute_name VARCHAR(50) PRIMARY KEY, mean_val DOUBLE, std_dev DOUBLE, weight_factor DOUBLE)");

            String upsert = "INSERT INTO fuzzy_stats_v2 VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE mean_val=?, std_dev=?, weight_factor=?";
            PreparedStatement ps = conn.prepareStatement(upsert);

            for (int i = 0; i < data.numAttributes(); i++) {
                if (i == data.classIndex()) continue;

                String attrName = data.attribute(i).name();
                AttributeStats stats = data.attributeStats(i);
                double mean = stats.numericStats.mean;
                double std = stats.numericStats.stdDev;
                double weight = Math.abs(eval.evaluateAttribute(i));

                if (Double.isNaN(mean)) mean = 0;
                if (Double.isNaN(std)) std = 1;

                ps.setString(1, attrName);
                ps.setDouble(2, mean);
                ps.setDouble(3, std);
                ps.setDouble(4, weight);
                ps.setDouble(5, mean);
                ps.setDouble(6, std);
                ps.setDouble(7, weight);
                ps.executeUpdate();
            }
        }
        loadSystem();
    }

    private static void trainJ48(Instances dataRaw) throws Exception {
        Remove remove = new Remove();
        remove.setAttributeIndices("1");
        remove.setInputFormat(dataRaw);
        Instances dataNoID = Filter.useFilter(dataRaw, remove);

        NumericToNominal convert = new NumericToNominal();
        convert.setAttributeIndices("last");
        convert.setInputFormat(dataNoID);
        Instances dataFinal = Filter.useFilter(dataNoID, convert);
        dataFinal.setClassIndex(dataFinal.numAttributes() - 1);

        classifier = new J48();
        classifier.buildClassifier(dataFinal);
        j48Rules = classifier.toString();
        
        try (Connection conn = DBConnect.getConnection()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(classifier);
            String sql = "INSERT INTO ai_models (model_type, model_blob, rules_text) VALUES ('J48', ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setBytes(1, baos.toByteArray());
            ps.setString(2, j48Rules);
            ps.executeUpdate();
        }
    }

    public static void loadSystem() {
        try (Connection conn = DBConnect.getConnection()) {
            means.clear(); stdDevs.clear(); weights.clear();
            
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM fuzzy_stats_v2");
            while (rs.next()) {
                String attr = rs.getString("attribute_name");
                means.put(attr, rs.getDouble("mean_val"));
                stdDevs.put(attr, rs.getDouble("std_dev"));
                weights.put(attr, rs.getDouble("weight_factor"));
            }

            rs = conn.createStatement().executeQuery("SELECT * FROM ai_models WHERE model_type='J48' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                j48Rules = rs.getString("rules_text");
                byte[] blob = rs.getBytes("model_blob");
                if (blob != null) {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));
                    classifier = (Classifier) ois.readObject();
                }
            }
        } catch (Exception e) {}
    }

    public static DiagnosisResult predictFuzzy(int ageY, int gender, int height, double weight, int apHi, int apLo, int chol, int gluc, int smoke, int alco, int active) {
        if (means.isEmpty()) loadSystem();
        if (means.isEmpty()) return new DiagnosisResult("MODEL BELUM SIAP", 0, "Harap latih model.", "");

        double logit = 0;
        StringBuilder log = new StringBuilder();
        StringBuilder rec = new StringBuilder();

        Map<String, Double> inputs = new HashMap<>();
        inputs.put("age_days", (double) ageY * 365);
        inputs.put("gender", (double) gender);
        inputs.put("height", (double) height);
        inputs.put("weight", weight);
        inputs.put("ap_hi", (double) apHi);
        inputs.put("ap_lo", (double) apLo);
        inputs.put("cholesterol", (double) chol);
        inputs.put("gluc", (double) gluc);
        inputs.put("smoke", (double) smoke);
        inputs.put("alco", (double) alco);
        inputs.put("active", (double) active);

        log.append("=== PERHITUNGAN LOG-ODDS (Z-SCORE x WEIGHT) ===\n");

        for (String attr : inputs.keySet()) {
            if (!means.containsKey(attr)) continue;

            double val = inputs.get(attr);
            double mean = means.get(attr);
            double std = stdDevs.get(attr);
            double weightAttr = weights.getOrDefault(attr, 0.0);

            if (std == 0) std = 1;
            double zScore = (val - mean) / std;

            if (attr.equals("active")) zScore = -zScore; 

            double contribution = 0;
            if (zScore > 0) {
                contribution = zScore * weightAttr;
                logit += contribution;
                
                log.append(String.format("- %s: Val=%.1f, Z=%.2f, W=%.3f -> Contrib=%.3f\n", 
                        attr, val, zScore, weightAttr, contribution));
                
                if (contribution > 0.5) {
                    rec.append("- Perhatian pada ").append(attr).append(" (High Impact)\n");
                }
            }
        }

        double probability = 1.0 / (1.0 + Math.exp(-logit));
        double percent = probability * 100;

        log.append("\n=== HASIL PROBABILITAS (SIGMOID) ===\n");
        log.append(String.format("Logit Sum: %.4f\n", logit));
        log.append(String.format("Probability: %.2f%%\n", percent));

        String level;
        if (probability > 0.8) level = "RISIKO TINGGI";
        else if (probability > 0.5) level = "RISIKO SEDANG";
        else level = "RISIKO RENDAH";

        if (rec.length() == 0) rec.append("Kondisi relatif aman berdasarkan statistik populasi.");

        return new DiagnosisResult(level, percent, rec.toString(), log.toString());
    }

    public static String getFuzzyRulesDocumentation() {
        if (weights.isEmpty()) loadSystem();
        StringBuilder sb = new StringBuilder();
        sb.append("BASIS PENGETAHUAN DINAMIS (DATA-DRIVEN)\n");
        sb.append("=======================================\n");
        sb.append("Bobot berikut dihasilkan otomatis dari analisis korelasi dataset.\n");
        sb.append("Rumus: Risk = Sigmoid(Sum(Z-Score * Weight))\n\n");
        
        weights.entrySet().stream()
               .sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
               .forEach(e -> {
                   sb.append(String.format("- %-12s : Bobot Pengaruh %.4f\n", e.getKey(), e.getValue()));
               });
               
        return sb.toString();
    }

    public static class DiagnosisResult {
        public String level;
        public double score;
        public String recommendation;
        public String calculationLog;

        public DiagnosisResult(String level, double score, String recommendation, String calculationLog) {
            this.level = level;
            this.score = score;
            this.recommendation = recommendation;
            this.calculationLog = calculationLog;
        }
    }
}