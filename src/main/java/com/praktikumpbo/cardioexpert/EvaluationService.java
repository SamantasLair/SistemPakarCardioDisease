package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

        public MatrixResult(int tp, int tn, int fp, int fn, double acc, double prec, double rec, double f1) {
            this.tp = tp;
            this.tn = tn;
            this.fp = fp;
            this.fn = fn;
            this.accuracy = acc;
            this.precision = prec;
            this.recall = rec;
            this.f1 = f1;
        }
    }

    public static MatrixResult getCachedOrCalculate() {
        MatrixResult cached = loadFromDb();
        if (cached != null) {
            return cached;
        }
        return recalculateAndSave();
    }

    public static MatrixResult loadFromDb() {
        try (Connection conn = DBConnect.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT * FROM model_evaluation WHERE id = 1");
            if (rs.next()) {
                return new MatrixResult(
                    rs.getInt("tp"), rs.getInt("tn"), rs.getInt("fp"), rs.getInt("fn"),
                    rs.getDouble("accuracy"), rs.getDouble("precision_val"), 
                    rs.getDouble("recall"), rs.getDouble("f1")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static MatrixResult recalculateAndSave() {
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

            MatrixResult res = new MatrixResult(tp, tn, fp, fn);
            saveToDb(res);
            return res;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new MatrixResult(0, 0, 0, 0);
    }

    private static void saveToDb(MatrixResult res) {
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "INSERT INTO model_evaluation (id, tp, tn, fp, fn, accuracy, precision_val, recall, f1, last_updated) " +
                         "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                         "ON DUPLICATE KEY UPDATE tp=?, tn=?, fp=?, fn=?, accuracy=?, precision_val=?, recall=?, f1=?, last_updated=NOW()";
            
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, res.tp);
            ps.setInt(2, res.tn);
            ps.setInt(3, res.fp);
            ps.setInt(4, res.fn);
            ps.setDouble(5, res.accuracy);
            ps.setDouble(6, res.precision);
            ps.setDouble(7, res.recall);
            ps.setDouble(8, res.f1);

            ps.setInt(9, res.tp);
            ps.setInt(10, res.tn);
            ps.setInt(11, res.fp);
            ps.setInt(12, res.fn);
            ps.setDouble(13, res.accuracy);
            ps.setDouble(14, res.precision);
            ps.setDouble(15, res.recall);
            ps.setDouble(16, res.f1);
            
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void clearStats() {
        try (Connection conn = DBConnect.getConnection()) {
            conn.createStatement().executeUpdate("TRUNCATE TABLE model_evaluation");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}