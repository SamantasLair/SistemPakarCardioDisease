package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.classifiers.trees.J48;
import weka.core.AttributeStats;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

public class InferenceEngine {

    private static final Logger logger = LoggerFactory.getLogger(InferenceEngine.class);

    public static Map<String, Double> means = new HashMap<>();
    public static Map<String, Double> stdDevs = new HashMap<>();
    public static Map<String, Double> weights = new HashMap<>();
    public static String j48Rules = "Model Legacy J48 belum dilatih.";

    public static void trainSystem(String csvPath) throws Exception {
        logger.info("[TRAIN] Initiating system training from: {}", csvPath);
        
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        loader.setFieldSeparator(";");
        Instances data = loader.getDataSet();
        
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        logger.info("[TRAIN] Dataset loaded. Instances: {}", data.numInstances());
        
        calculateStatisticsAndWeights(data);
        trainJ48(data); 
    }
    
    public static void loadSystem() {
        logger.info("[SYSTEM] Loading fuzzy parameters and statistics from DB...");
        try (Connection conn = DBConnect.getConnection()) {
            means.clear(); stdDevs.clear(); weights.clear();
            
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM fuzzy_stats_v2");
            int count = 0;
            while (rs.next()) {
                String attr = rs.getString("attribute_name");
                double m = rs.getDouble("mean_val");
                double s = rs.getDouble("std_dev");
                double w = rs.getDouble("weight_factor");
                
                means.put(attr, m);
                stdDevs.put(attr, s);
                weights.put(attr, w);
                count++;
            }
            logger.info("[SYSTEM] Parameters loaded: {}", count);

            rs = conn.createStatement().executeQuery("SELECT * FROM ai_models WHERE model_type='J48' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                j48Rules = rs.getString("rules_text");
            }
        } catch (Exception e) {
            logger.error("[SYSTEM] Failed to load system", e);
        }
    }

    private static void calculateStatisticsAndWeights(Instances data) throws Exception {
        CorrelationAttributeEval eval = new CorrelationAttributeEval();
        boolean wekaAvailable = false;
        try {
            eval.buildEvaluator(data);
            wekaAvailable = true;
        } catch (Throwable t) { 
            logger.warn("[STATS] Weka Native Library failed. Switching to Manual Pearson Correlation.");
        }

        try (Connection conn = DBConnect.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fuzzy_stats_v2 (attribute_name VARCHAR(50) PRIMARY KEY, mean_val DOUBLE, std_dev DOUBLE, weight_factor DOUBLE)");

            String upsert = "INSERT INTO fuzzy_stats_v2 VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE mean_val=?, std_dev=?, weight_factor=?";
            PreparedStatement ps = conn.prepareStatement(upsert);

            for (int i = 0; i < data.numAttributes(); i++) {
                if (i == data.classIndex()) continue;

                String attrName = data.attribute(i).name();
                double mean = 0.0;
                double std = 1.0;
                double weight = 0.0;

                if (wekaAvailable) {
                    try {
                        weight = Math.abs(eval.evaluateAttribute(i));
                    } catch (Exception ex) {
                        weight = calculateSimpleCorrelation(data, i, data.classIndex());
                    }
                } else {
                    weight = calculateSimpleCorrelation(data, i, data.classIndex());
                }

                if (data.attribute(i).isNumeric()) {
                    AttributeStats stats = data.attributeStats(i);
                    if (stats.numericStats != null) {
                        mean = stats.numericStats.mean;
                        std = stats.numericStats.stdDev;
                    }
                }

                if (Double.isNaN(mean)) mean = 0;
                if (Double.isNaN(std) || std == 0) std = 1;

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

    private static double calculateSimpleCorrelation(Instances data, int attrIndex, int classIndex) {
        double sumX = 0, sumY = 0, sumXY = 0;
        double sumX2 = 0, sumY2 = 0;
        int n = data.numInstances();

        for (int i = 0; i < n; i++) {
            Instance inst = data.instance(i);
            double x = inst.value(attrIndex);
            double y = inst.value(classIndex);

            sumX += x;
            sumY += y;
            sumXY += (x * y);
            sumX2 += (x * x);
            sumY2 += (y * y);
        }

        double numerator = (n * sumXY) - (sumX * sumY);
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator == 0) return 0.001;
        return Math.abs(numerator / denominator);
    }

    private static void trainJ48(Instances dataRaw) throws Exception {
        try {
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
            sb.append("=== LEGACY J48 DECISION TREE ===\n");
            sb.append(classifier.toString());
            j48Rules = sb.toString();
            
            try (Connection conn = DBConnect.getConnection()) {
                String sql = "INSERT INTO ai_models (model_type, model_blob, rules_text) VALUES ('J48', ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setBytes(1, new byte[0]); 
                ps.setString(2, j48Rules);
                ps.executeUpdate();
            }
        } catch (Throwable t) {
            logger.error("[J48] Failed to train J48: {}", t.getMessage());
            j48Rules = "Training Failed: " + t.getMessage();
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
                String advice = getMedicalAdvice(attr, inputVal, height);
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

    private static String getMedicalAdvice(String attr, double val, double height) {
        switch (attr) {
            case "ap_hi":
                if (val >= 140) return "HYPERTENSIVE CRISIS (>140): Consult a doctor immediately.";
                if (val >= 130) return "HYPERTENSION STAGE 1: Reduce sodium, manage stress, monitor BP daily.";
                return "";
            case "ap_lo":
                if (val >= 90) return "HIGH DIASTOLIC (>90): Heart is under severe stress even at rest.";
                return "";
            case "cholesterol":
                if (val == 3) return "DANGEROUS CHOLESTEROL: Avoid trans fats entirely. Check lipid profile.";
                if (val == 2) return "ELEVATED CHOLESTEROL: Increase fiber intake and healthy fats.";
                return "";
            case "gluc":
                if (val == 3) return "DIABETES INDICATION: Blood sugar is critically high. Consult an Endocrinologist.";
                if (val == 2) return "PRE-DIABETES: Reduce simple sugars and processed carbs.";
                return "";
            case "smoke":
                return "STOP SMOKING: Major cause of arterial damage.";
            case "alco":
                return "LIMIT ALCOHOL: Excessive intake weakens heart muscle.";
            case "weight":
                double bmi = val / Math.pow(height / 100.0, 2);
                if (bmi >= 30) return String.format("OBESITY (BMI %.1f): Caloric deficit diet required.", bmi);
                if (bmi >= 25) return String.format("OVERWEIGHT (BMI %.1f): Increase physical activity.", bmi);
                return "";
            case "active":
                return "SEDENTARY LIFESTYLE: Aim for 30 mins of walking daily.";
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
        
        // SECTION 1: STATISTICAL WEIGHTS
        sb.append("=== SECTION 1: STATISTICAL KNOWLEDGE BASE ===\n");
        sb.append("Source: Dataset Pearson Correlation Analysis\n");
        sb.append("---------------------------------------------\n");
        weights.entrySet().stream()
               .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
               .forEach(e -> sb.append(String.format("FACTOR: %-12s | Weight: %.4f\n", e.getKey(), e.getValue())));
        
        sb.append("\n");
        
        // SECTION 2: CLINICAL RULES
        sb.append("=== SECTION 2: CLINICAL RULE BASE (ALL RULES) ===\n");
        sb.append("Source: ACC/AHA & WHO Guidelines\n");
        sb.append("---------------------------------------------\n");
        sb.append(getStaticRulesDescription());

        return sb.toString();
    }

    private static String getStaticRulesDescription() {
        return "1. BLOOD PRESSURE (ACC/AHA 2017):\n" +
               "   - IF ap_hi >= 140 THEN Risk=1.0 (CRISIS)\n" +
               "   - IF ap_hi >= 130 THEN Risk=0.8 (STAGE 1)\n" +
               "   - IF ap_hi >= 120 THEN Risk=0.5 (ELEVATED)\n" +
               "   - IF ap_lo >= 90  THEN Risk=1.0 (STAGE 2)\n" +
               "   - IF ap_lo >= 80  THEN Risk=0.8 (STAGE 1)\n\n" +
               "2. BMI / WEIGHT (WHO):\n" +
               "   - IF BMI >= 30 THEN Risk=1.0 (OBESE)\n" +
               "   - IF BMI >= 25 THEN Risk=0.6 (OVERWEIGHT)\n\n" +
               "3. LAB RESULTS:\n" +
               "   - IF Cholesterol=3 (Very High) THEN Risk=1.0\n" +
               "   - IF Glucose=3 (Very High)     THEN Risk=1.0\n\n" +
               "4. LIFESTYLE:\n" +
               "   - IF Smoker=Yes THEN Risk=1.0\n" +
               "   - IF Active=No  THEN Risk=1.0\n\n" +
               "5. SYNERGISTIC RISKS (MULTIPLIERS):\n" +
               "   - IF (Hypertension AND Diabetes) THEN Score *= 1.25\n" +
               "   - IF (Smoker AND HeartCondition) THEN Score *= 1.20";
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