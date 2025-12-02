package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class EvaluationService {
    
    public static class MatrixResult {
        public int tp, tn, fp, fn;
        public double accuracy, precision, recall, f1;

        public MatrixResult(int tp, int tn, int fp, int fn) {
            this.tp = tp;
            this.tn = tn;
            this.fp = fp;
            this.fn = fn;
            
            int total = tp + tn + fp + fn;
            this.accuracy = total == 0 ? 0 : (double) (tp + tn) / total;
            this.precision = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
            this.recall = (tp + fn) == 0 ? 0 : (double) tp / (tp + fn);
            this.f1 = (precision + recall) == 0 ? 0 : 2 * (precision * recall) / (precision + recall);
        }
    }

    public static MatrixResult evaluateModel() {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        
        try (Connection conn = DBConnect.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT age_days, gender, height, weight, ap_hi, ap_lo, cholesterol, gluc, smoke, alco, active, cardio FROM dataset_cardio";
            ResultSet rs = stmt.executeQuery(query);
            
            while (rs.next()) {
                int actual = rs.getInt("cardio");
                
                int ageY = rs.getInt("age_days") / 365;
                int gender = rs.getInt("gender");
                int height = rs.getInt("height");
                double weight = rs.getDouble("weight");
                int apHi = rs.getInt("ap_hi");
                int apLo = rs.getInt("ap_lo");
                int chol = rs.getInt("cholesterol");
                int gluc = rs.getInt("gluc");
                int smoke = rs.getInt("smoke");
                int alco = rs.getInt("alco");
                int active = rs.getInt("active");

                InferenceEngine.DiagnosisResult result = InferenceEngine.predictSugeno(
                    ageY, gender, height, weight, apHi, apLo, chol, gluc, smoke, alco, active
                );

                int predicted = result.score >= 35.0 ? 1 : 0;

                if (predicted == 1 && actual == 1) tp++;
                else if (predicted == 0 && actual == 0) tn++;
                else if (predicted == 1 && actual == 0) fp++;
                else if (predicted == 0 && actual == 1) fn++;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return new MatrixResult(tp, tn, fp, fn);
    }
}