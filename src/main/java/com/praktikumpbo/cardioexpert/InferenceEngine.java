package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.classifiers.trees.J48;
import weka.core.AttributeStats;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

public class InferenceEngine {

    public static Map<String, Double> means = new HashMap<>();
    public static Map<String, Double> stdDevs = new HashMap<>();
    public static Map<String, Double> weights = new HashMap<>();
    public static String j48Rules = "Model Legacy J48 belum dilatih.";

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
            }
        } catch (Exception e) {}
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

        J48 classifier = new J48();
        classifier.setOptions(new String[]{"-C", "0.25", "-M", "2"});
        classifier.buildClassifier(dataFinal);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== LEGACY J48 DECISION TREE (REFERENSI SAJA) ===\n");
        sb.append("Model ini tidak digunakan untuk perhitungan risiko aktif.\n");
        sb.append("Hanya sebagai pembanding struktur keputusan statis.\n\n");
        sb.append(classifier.toString());
        j48Rules = sb.toString();
        
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "INSERT INTO ai_models (model_type, model_blob, rules_text) VALUES ('J48', ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setBytes(1, new byte[0]); 
            ps.setString(2, j48Rules);
            ps.executeUpdate();
        }
    }

    public static DiagnosisResult predictSugeno(int ageY, int gender, int height, double weight, int apHi, int apLo, int chol, int gluc, int smoke, int alco, int active) {
        if (means.isEmpty()) loadSystem();
        
        StringBuilder calcLog = new StringBuilder();
        StringBuilder rulesLog = new StringBuilder();
        StringBuilder rec = new StringBuilder();

        Map<String, Double> inputs = new HashMap<>();
        inputs.put("age", (double) ageY);
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

        calcLog.append("=== LANGKAH 1: HYBRID INFERENCE (GAUSSIAN & CRISP) ===\n");
        calcLog.append("Metode: Sugeno Hybrid (Continuous -> Gaussian, Categorical -> Crisp)\n");
        calcLog.append("Tujuan: Menghitung total risiko terbobot secara adil.\n\n");
        
        double currentRiskSum = 0;
        double totalPossibleWeight = 0;

        for (String attr : inputs.keySet()) {
            String dbKey = attr.equals("age") ? "age_days" : attr; 
            if (!weights.containsKey(dbKey)) continue;

            double inputVal = inputs.get(attr);
            double corrWeight = weights.get(dbKey);
            double alpha = 0;
            String method = "";

            if (isContinuous(attr)) {
                double dbVal = inputVal; 
                if(attr.equals("age")) dbVal = inputVal * 365; 

                double mean = means.get(dbKey);
                double std = stdDevs.get(dbKey);
                
                double mu = Math.exp(-0.5 * Math.pow((dbVal - mean) / std, 2));
                alpha = 1.0 - mu;
                method = String.format("Gaussian (Mean=%.1f, Std=%.1f)", (attr.equals("age")?mean/365:mean), (attr.equals("age")?std/365:std));
            
            } else {
                alpha = getCrispRisk(attr, inputVal);
                method = "Crisp Mapping (Tabel Risiko)";
            }
            
            double attributeRiskContribution = alpha * corrWeight;
            currentRiskSum += attributeRiskContribution;
            totalPossibleWeight += corrWeight;

            calcLog.append(String.format("[VAR] %s = %.0f\n", attr, inputVal));
            calcLog.append(String.format("   Metode: %s\n", method));
            calcLog.append(String.format("   Bobot Korelasi (W) = %.4f\n", corrWeight));
            calcLog.append(String.format("   Level Risiko (Alpha) = %.4f\n", alpha));
            calcLog.append(String.format("   Kontribusi (Alpha * W) = %.4f\n\n", attributeRiskContribution));
            
            if(alpha > 0.1) {
                rulesLog.append(String.format("IF %s = %.0f THEN RiskLevel = %.2f (Weight %.2f)\n", attr, inputVal, alpha, corrWeight));
            }

            if (alpha > 0.6) {
                String advice = getMedicalAdvice(attr, inputVal);
                if (!advice.isEmpty()) {
                    rec.append("• ").append(advice).append("\n");
                }
            }
        }

        calcLog.append("=== LANGKAH 2: DEFUZZIFIKASI (NORMALIZED SUM) ===\n");
        double finalPercentage = 0;
        if (totalPossibleWeight > 0) {
            calcLog.append(String.format("Total Risiko Terkumpul = %.4f\n", currentRiskSum));
            calcLog.append(String.format("Total Bobot Maksimal   = %.4f\n", totalPossibleWeight));
            
            finalPercentage = (currentRiskSum / totalPossibleWeight) * 100;
            
            calcLog.append(String.format("Persentase Akhir = (%.4f / %.4f) * 100 = %.2f%%\n", 
                    currentRiskSum, totalPossibleWeight, finalPercentage));
        }
        
        String level;
        if (finalPercentage > 60) level = "RISIKO TINGGI";
        else if (finalPercentage > 30) level = "RISIKO SEDANG";
        else level = "RISIKO RENDAH";

        if (rec.length() == 0) rec.append("Parameter vital Anda berada dalam batas normal. Pertahankan gaya hidup sehat.");
        else rec.insert(0, "BERIKUT SARAN MEDIS BERDASARKAN HASIL ANALISIS:\n");

        return new DiagnosisResult(level, finalPercentage, rec.toString(), calcLog.toString(), rulesLog.toString());
    }

    private static String getMedicalAdvice(String attr, double val) {
        double meanVal = means.getOrDefault(attr, 0.0);

        switch (attr) {
            case "ap_hi":
                if (val > meanVal)
                    return "Tekanan Darah Sistolik Tinggi (Hipertensi): Kurangi konsumsi garam/natrium, hindari stres, dan pantau tekanan darah rutin.";
                else
                    return "Tekanan Darah Sistolik Rendah (Hipotensi): Pastikan hidrasi cukup dan konsultasi jika sering pusing.";
            
            case "ap_lo":
                if (val > meanVal)
                    return "Tekanan Darah Diastolik Tinggi: Waspada risiko beban kerja jantung. Konsultasikan dengan dokter untuk manajemen tensi.";
                else
                    return "Tekanan Darah Diastolik Rendah: Biasanya tidak berbahaya kecuali disertai gejala pusing/lemas.";

            case "cholesterol":
                return (val == 3) ? "Kolesterol Sangat Tinggi: Risiko penyumbatan arteri. Hindari lemak jenuh (gorengan/santan) dan segera cek profil lipid lengkap." 
                                  : "Kolesterol Diatas Normal: Mulai kurangi makanan berlemak dan tingkatkan asupan serat.";
            
            case "gluc":
                return (val == 3) ? "Gula Darah Sangat Tinggi: Indikasi kuat Diabetes. Segera lakukan tes HbA1c dan konsultasi ke dokter penyakit dalam."
                                  : "Gula Darah Diatas Normal: Kurangi asupan gula/karbohidrat sederhana dan perbanyak aktivitas fisik.";
            
            case "smoke":
                return "Perokok Aktif: Merokok adalah faktor risiko utama kerusakan pembuluh darah jantung. Sangat disarankan untuk berhenti.";
            
            case "alco":
                return "Konsumsi Alkohol: Batasi atau hentikan konsumsi alkohol untuk menjaga kesehatan otot jantung dan liver.";
            
            case "weight":
                if (val > meanVal) {
                    return "Berat Badan Berlebih (Overweight): Meningkatkan beban kerja jantung. Disarankan diet defisit kalori dan olahraga teratur.";
                } else {
                    return "Berat Badan Kurang (Underweight): Terlalu rendah dari statistik normal. Pastikan asupan nutrisi mencukupi.";
                }

            case "height":
                return ""; 
            
            case "active":
                return "Kurang Aktivitas Fisik: Gaya hidup sedenter meningkatkan risiko kardiovaskular. Usahakan jalan kaki minimal 30 menit sehari.";
            
            case "age":
                return "Faktor Usia: Risiko meningkat seiring usia. Lakukan medical check-up rutin minimal 6 bulan sekali.";
            
            default:
                return "";
        }
    }

    private static boolean isContinuous(String attr) {
        return attr.equals("age") || attr.equals("height") || attr.equals("weight") || 
               attr.equals("ap_hi") || attr.equals("ap_lo");
    }

    private static double getCrispRisk(String attr, double val) {
        int v = (int) val;
        switch (attr) {
            case "cholesterol":
            case "gluc":
                if (v == 1) return 0.0;      
                if (v == 2) return 0.6;      
                if (v == 3) return 1.0;      
                break;
            case "smoke":
            case "alco":
                if (v == 0) return 0.0;      
                if (v == 1) return 1.0;      
                break;
            case "active":
                if (v == 1) return 0.0;      
                if (v == 0) return 1.0;     
                break;
            case "gender":
                if (v == 1) return 0.0;      
                if (v == 2) return 0.3;      
                break;
        }
        return 0.0;
    }

    public static String getFuzzyRulesDocumentation() {
        if (weights.isEmpty()) loadSystem();
        StringBuilder sb = new StringBuilder();
        sb.append("BASIS PENGETAHUAN HYBRID (SUGENO)\n");
        sb.append("=================================\n");
        sb.append("1. Variabel Kontinu (Tensi, Usia, dll) -> Gaussian Fuzzy\n");
        sb.append("2. Variabel Kategori (Rokok, Kolesterol) -> Crisp Mapping\n\n");
        
        weights.entrySet().stream()
               .sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
               .forEach(e -> {
                   sb.append(String.format("FAKTOR: %-12s | Bobot: %.4f\n", e.getKey(), e.getValue()));
               });
        return sb.toString();
    }

    public static class DiagnosisResult {
        public String level;
        public double score;
        public String recommendation;
        public String calcLog;
        public String rulesActive;

        public DiagnosisResult(String level, double score, String recommendation, String calcLog, String rulesActive) {
            this.level = level;
            this.score = score;
            this.recommendation = recommendation;
            this.calcLog = calcLog;
            this.rulesActive = rulesActive;
        }
    }
    
    public static void updateVariableStandard(String attr, double newMean, double newStd) {
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "UPDATE fuzzy_stats_v2 SET mean_val=?, std_dev=? WHERE attribute_name=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDouble(1, newMean);
            ps.setDouble(2, newStd);
            ps.setString(3, attr);
            int affected = ps.executeUpdate();
            
            if (affected > 0) {
                means.put(attr, newMean);
                stdDevs.put(attr, newStd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}