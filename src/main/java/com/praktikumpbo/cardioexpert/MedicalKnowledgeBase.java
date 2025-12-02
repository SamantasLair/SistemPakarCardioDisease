package com.praktikumpbo.cardioexpert;

public class MedicalKnowledgeBase {

    public static String getMedicalAdvice(String attr, double val, double height) {
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

    public static String getStaticRulesDescription() {
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
}