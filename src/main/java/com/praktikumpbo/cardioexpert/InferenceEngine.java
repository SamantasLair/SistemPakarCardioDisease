package com.praktikumpbo.cardioexpert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
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

    private static final String[] ALL_COLS = {
        "age_days", "gender", "height", "weight", "ap_hi", "ap_lo", 
        "cholesterol", "gluc", "smoke", "alco", "active"
    };

    public static void trainSystem(String csvPath) throws Exception {
        trainJ48(csvPath);
        calculateFuzzyStats();
    }

    private static void trainJ48(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        loader.setFieldSeparator(";"); 
        Instances dataRaw = loader.getDataSet();

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
        saveJ48ToDB();
    }

    private static void calculateFuzzyStats() {
        try (Connection conn = DBConnect.getConnection()) {
            for (String col : ALL_COLS) {
                String sql = "SELECT AVG(" + col + "), STDDEV(" + col + ") FROM dataset_cardio";
                ResultSet rs = conn.createStatement().executeQuery(sql);
                if (rs.next()) {
                    double mean = rs.getDouble(1);
                    double std = rs.getDouble(2);
                    
                    String upsert = "INSERT INTO fuzzy_stats VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE mean_val=?, std_dev=?";
                    PreparedStatement ps = conn.prepareStatement(upsert);
                    ps.setString(1, col); ps.setDouble(2, mean); ps.setDouble(3, std);
                    ps.setDouble(4, mean); ps.setDouble(5, std);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void saveJ48ToDB() throws Exception {
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
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM ai_models WHERE model_type='J48' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                j48Rules = rs.getString("rules_text");
                byte[] blob = rs.getBytes("model_blob");
                if (blob != null) {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));
                    classifier = (Classifier) ois.readObject();
                }
            }
            rs = conn.createStatement().executeQuery("SELECT * FROM fuzzy_stats");
            while (rs.next()) {
                means.put(rs.getString("attribute_name"), rs.getDouble("mean_val"));
                stdDevs.put(rs.getString("attribute_name"), rs.getDouble("std_dev"));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ===== KEMBALIKAN METHOD INI =====
    public static String getFuzzyRulesDocumentation() {
        return "BASIS PENGETAHUAN (FUZZY SUGENO STATISTICAL)\n" +
               "============================================\n" +
               "Logika didasarkan pada Z-Score Populasi Data.\n\n" +
               "1. TEKANAN DARAH\n" +
               "   - IF Z-Score(Tensi) > 1.0 THEN Skor +30 (Bahaya)\n" +
               "   - IF Z-Score(Tensi) > 0.5 THEN Skor +15 (Waspada)\n\n" +
               "2. INDEKS MASSA TUBUH (BMI)\n" +
               "   - IF BMI > 30 THEN Skor +20 (Obesitas)\n" +
               "   - IF BMI > 25 THEN Skor +10 (Overweight)\n\n" +
               "3. FAKTOR USIA\n" +
               "   - IF Z-Score(Usia) > 1.0 THEN Skor +10\n\n" +
               "4. HASIL LAB (Kolesterol & Gula)\n" +
               "   - IF Z-Score(Chol) > 1.0 THEN Skor +20\n" +
               "   - IF Z-Score(Gluc) > 1.0 THEN Skor +20\n\n" +
               "5. GAYA HIDUP\n" +
               "   - Merokok: Skor +15\n" +
               "   - Alkohol: Skor +10\n" +
               "   - Tidak Aktif: Skor +10\n" +
               "   - Aktif Olahraga: Skor -5 (Bonus)\n\n" +
               "--------------------------------------------\n" +
               "KEPUTUSAN AKHIR (Total Skor):\n" +
               "   - Skor >= 60 : RISIKO TINGGI\n" +
               "   - Skor >= 30 : RISIKO SEDANG\n" +
               "   - Skor <  30 : RISIKO RENDAH\n";
    }

    public static DiagnosisResult predictFuzzy(int ageY, int gender, int height, double weight, int apHi, int apLo, int chol, int gluc, int smoke, int alco, int active) {
        if (means.isEmpty()) loadSystem();
        if (means.isEmpty()) return new DiagnosisResult("MODEL BELUM SIAP", 0, "Harap latih model.", "");

        double score = 0;
        StringBuilder rec = new StringBuilder();
        StringBuilder log = new StringBuilder();

        double heightM = height / 100.0;
        double bmi = weight / (heightM * heightM);

        log.append("=== 1. FUZZIFIKASI DATA (Z-Score) ===\n");
        
        double ageDays = ageY * 365.0;
        double zAge = getZScore("age_days", ageDays);
        double zWeight = getZScore("weight", weight);
        double zApHi = getZScore("ap_hi", (double)apHi);
        
        log.append(String.format("Umur: %.2f (Z: %.2f)\n", ageDays, zAge));
        log.append(String.format("Tensi Hi: %d (Z: %.2f)\n", apHi, zApHi));
        log.append(String.format("BMI: %.2f\n", bmi));

        log.append("\n=== 2. INFERENSI ===\n");

        if (zApHi > 1.0) { 
            score += 30; 
            rec.append("- Tekanan Darah Tinggi (Signifikan di atas rata-rata).\n"); 
            log.append("[BAHAYA] Tensi Sangat Tinggi (+30)\n");
        } else if (zApHi > 0.5) {
            score += 15;
            log.append("[WASPADA] Tensi Agak Tinggi (+15)\n");
        }

        if (zAge > 1.0) { 
            score += 10; 
            log.append("[FAKTOR] Usia Lanjut (+10)\n");
        }

        if (chol > 1) { 
            score += 20; 
            rec.append("- Kolesterol Jauh Diatas Normal.\n"); 
            log.append("[BAHAYA] Kolesterol Tinggi (+20)\n");
        }

        if (gluc > 1) { 
            score += 20; 
            rec.append("- Gula Darah Tinggi.\n"); 
            log.append("[BAHAYA] Gula Darah Tinggi (+20)\n");
        }

        if (bmi > 30) {
            score += 20;
            rec.append("- Berat Badan Berlebih (Obesitas).\n");
            log.append("[RISIKO] BMI Tinggi (+20)\n");
        } else if (bmi > 25) {
            score += 10;
            log.append("[RISIKO] BMI Overweight (+10)\n");
        }
        
        if (zWeight > 1.0) {
            score += 15;
            log.append("[RISIKO] Berat Badan vs Populasi (+15)\n");
        }

        if (gender == 2) { 
            score += 5;
            log.append("[FAKTOR] Gender Pria (+5)\n");
        }

        if (smoke == 1) { 
            score += 15; 
            rec.append("- Berhenti Merokok.\n"); 
            log.append("[RISIKO] Perokok (+15)\n");
        }

        if (alco == 1) {
            score += 10;
            rec.append("- Kurangi Konsumsi Alkohol.\n");
            log.append("[RISIKO] Alkohol (+10)\n");
        }

        if (active == 0) {
            score += 10;
            rec.append("- Kurang Gerak. Perbanyak olahraga.\n");
            log.append("[RISIKO] Tidak Aktif (+10)\n");
        } else {
            score -= 5; 
            log.append("[BONUS] Aktif Fisik (-5)\n");
        }

        if (score < 0) score = 0;

        log.append("\n=== 3. DEFUZZIFIKASI ===\n");
        log.append("Total Skor Risiko: " + score + "\n");

        String level;
        if (score >= 60) level = "RISIKO TINGGI";
        else if (score >= 30) level = "RISIKO SEDANG";
        else level = "RISIKO RENDAH";
        
        log.append("Keputusan Akhir: " + level);

        if (rec.length() == 0) rec.append("- Pertahankan gaya hidup sehat Anda.");

        return new DiagnosisResult(level, score, rec.toString(), log.toString());
    }

    private static double getZScore(String attr, double val) {
        if (!means.containsKey(attr)) return 0;
        double mu = means.get(attr);
        double sigma = stdDevs.get(attr);
        if (sigma == 0) return 0;
        return (val - mu) / sigma;
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