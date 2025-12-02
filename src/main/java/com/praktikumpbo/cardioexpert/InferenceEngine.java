package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InferenceEngine {

    public static Map<String, Double> means = new HashMap<>();
    public static Map<String, Double> stdDevs = new HashMap<>();
    public static Map<String, Double> weights = new HashMap<>();

    public static void loadSystem() {
        try (Connection conn = DBConnect.getConnection()) {
            means.clear(); stdDevs.clear(); weights.clear();
            
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM fuzzy_stats_v2");
            while (rs.next()) {
                String attr = rs.getString("attribute_name");
                double m = rs.getDouble("mean_val");
                double s = rs.getDouble("std_dev");
                double w = rs.getDouble("weight_factor");
                
                means.put(attr, m);
                stdDevs.put(attr, s);
                weights.put(attr, w);
            }
            LegacyService.loadLegacyModel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static DiagnosisResult predictSugeno(int ageY, int gender, int height, double weight, int apHi, int apLo, int chol, int gluc, int smoke, int alco, int active) {
        if (means.isEmpty()) loadSystem();
        
        StringBuilder calcLog = new StringBuilder();
        StringBuilder rulesLog = new StringBuilder();
        List<String> recommendations = new ArrayList<>();

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

        calcLog.append("=== SYSTEM LOG: HYBRID INFERENCE ENGINE ===\n");
        calcLog.append(String.format("Timestamp: %s\n", java.time.LocalDateTime.now()));
        calcLog.append("----------------------------------------------------------------\n");
        
        double currentRiskSum = 0;
        double totalPossibleWeight = 0;

        boolean isHypertensive = false;
        boolean isObese = false;
        boolean isDiabetic = (gluc == 3);
        boolean isSmoker = (smoke == 1);
        boolean highChol = (chol == 3);

        rulesLog.append("=== TRIGGERED RULES FOR CURRENT PATIENT ===\n");

        for (String attr : inputs.keySet()) {
            String dbKey = attr.equals("age") ? "age_days" : attr; 
            if (!weights.containsKey(dbKey) && weights.containsKey(attr)) dbKey = attr;
            if (!weights.containsKey(dbKey)) continue;

            double inputVal = inputs.get(attr);
            double corrWeight = weights.get(dbKey);
            double alpha = 0;
            String condition = "NORMAL";

            if (isContinuous(attr)) {
                double dbVal = (attr.equals("age")) ? inputVal * 365 : inputVal;
                double mean = means.get(dbKey);
                double std = stdDevs.get(dbKey);
                double z = (dbVal - mean) / std;
                double mu = Math.exp(-0.5 * Math.pow(z, 2));
                alpha = 1.0 - mu;
                
                if (attr.equals("ap_hi")) {
                    if (inputVal >= 140) { condition = "> 140 (CRISIS)"; isHypertensive = true; alpha = 1.0; }
                    else if (inputVal >= 130) { condition = "> 130 (STG 1)"; isHypertensive = true; alpha = 0.8; }
                    else if (inputVal >= 120) { condition = "> 120 (ELEVATED)"; alpha = 0.5; }
                } else if (attr.equals("ap_lo")) {
                    if (inputVal >= 90) { condition = "> 90 (STG 2)"; isHypertensive = true; alpha = 1.0; }
                    else if (inputVal >= 80) { condition = "> 80 (STG 1)"; isHypertensive = true; alpha = 0.8; }
                } else if (attr.equals("weight")) {
                    double bmi = weight / Math.pow(height / 100.0, 2);
                    if (bmi >= 30) { condition = "BMI > 30 (OBESE)"; isObese = true; alpha = 1.0; }
                    else if (bmi >= 25) { condition = "BMI > 25 (OVERWEIGHT)"; alpha = 0.6; }
                }

            } else {
                alpha = getCrispRisk(attr, inputVal);
                if (alpha >= 1.0) condition = "HIGH RISK";
                else if (alpha >= 0.5) condition = "MODERATE RISK";
            }
            
            double contribution = alpha * corrWeight;
            currentRiskSum += contribution;
            totalPossibleWeight += corrWeight;

            calcLog.append(String.format("VAR: %-12s | Val: %-4.0f | Risk: %.2f | W: %.4f | Contrib: %.4f\n", attr.toUpperCase(), inputVal, alpha, corrWeight, contribution));
            
            if (alpha > 0.4) {
                rulesLog.append(String.format("IF %s IS %s THEN Risk += %.4f (Base W: %.4f)\n", attr.toUpperCase(), condition, contribution, corrWeight));
                String advice = MedicalKnowledgeBase.getMedicalAdvice(attr, inputVal, height);
                if (!advice.isEmpty() && !recommendations.contains(advice)) recommendations.add(advice);
            }
        }

        calcLog.append("----------------------------------------------------------------\n");

        if (isHypertensive && isDiabetic) {
            currentRiskSum *= 1.25; 
            rulesLog.append("IF HYPERTENSION AND DIABETES THEN Multiply Score by 1.25 (Synergy)\n");
            recommendations.add(0, "CRITICAL: The combination of Hypertension and High Glucose exponentially increases stroke risk.");
        }
        if (isSmoker && (isHypertensive || highChol)) {
            currentRiskSum *= 1.20;
            rulesLog.append("IF SMOKER AND (HYPERTENSION OR HIGH_CHOLESTEROL) THEN Multiply Score by 1.20\n");
            recommendations.add(0, "CRITICAL: Smoking with cardiovascular conditions severely damages arteries.");
        }

        double finalPercentage = 0;
        if (totalPossibleWeight > 0) {
            finalPercentage = (currentRiskSum / totalPossibleWeight) * 100;
        }
        if (finalPercentage > 100) finalPercentage = 100; 

        calcLog.append(String.format("FINAL WEIGHTED SCORE : %.4f\n", currentRiskSum));
        calcLog.append(String.format("RISK PERCENTAGE      : %.2f%%\n", finalPercentage));

        String level;
        if (finalPercentage >= 60) level = "RISIKO TINGGI";
        else if (finalPercentage >= 30) level = "RISIKO SEDANG";
        else level = "RISIKO RENDAH";

        StringBuilder recString = new StringBuilder();
        if (recommendations.isEmpty()) {
            recString.append("Clinical parameters are within safe limits. Maintain a healthy lifestyle.");
        } else {
            recString.append("MEDICAL RECOMMENDATIONS (ACC/AHA/WHO GUIDELINES):\n");
            for (String rec : recommendations) {
                recString.append("• ").append(rec).append("\n");
            }
        }

        return new DiagnosisResult(level, finalPercentage, recString.toString(), calcLog.toString(), rulesLog.toString());
    }

    private static boolean isContinuous(String attr) {
        return attr.equals("age") || attr.equals("height") || attr.equals("weight") || 
               attr.equals("ap_hi") || attr.equals("ap_lo");
    }

    private static double getCrispRisk(String attr, double val) {
        int v = (int) val;
        switch (attr) {
            case "cholesterol": case "gluc":
                if (v == 2) return 0.5; if (v == 3) return 1.0; break;
            case "smoke": case "alco": case "active":
                if (v == 1 && !attr.equals("active")) return 1.0; 
                if (v == 0 && attr.equals("active")) return 1.0;
                break;
            case "gender":
                if (v == 2) return 0.2; break;
        }
        return 0.0;
    }

    public static String getFuzzyRulesDocumentation() {
        if (weights.isEmpty()) loadSystem();
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== SECTION 1: STATISTICAL KNOWLEDGE BASE ===\n");
        sb.append("Source: Dataset Pearson Correlation Analysis\n");
        sb.append("---------------------------------------------\n");
        weights.entrySet().stream()
               .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
               .forEach(e -> sb.append(String.format("FACTOR: %-12s | Weight: %.4f\n", e.getKey(), e.getValue())));
        
        sb.append("\n");
        sb.append("=== SECTION 2: CLINICAL RULE BASE (ALL RULES) ===\n");
        sb.append("Source: ACC/AHA & WHO Guidelines\n");
        sb.append("---------------------------------------------\n");
        sb.append(MedicalKnowledgeBase.getStaticRulesDescription());

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
}