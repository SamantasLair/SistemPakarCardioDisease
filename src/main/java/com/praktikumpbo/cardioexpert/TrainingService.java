package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.core.Attribute;
import weka.core.AttributeStats;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

public class TrainingService {

    public static void trainSystem(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        loader.setFieldSeparator(";");
        Instances data = loader.getDataSet();
        processTraining(data);
    }

    public static void trainSystemFromDb() {
        try (Connection conn = DBConnect.getConnection()) {
            ArrayList<Attribute> atts = new ArrayList<>();
            atts.add(new Attribute("age_days"));
            atts.add(new Attribute("gender"));
            atts.add(new Attribute("height"));
            atts.add(new Attribute("weight"));
            atts.add(new Attribute("ap_hi"));
            atts.add(new Attribute("ap_lo"));
            atts.add(new Attribute("cholesterol"));
            atts.add(new Attribute("gluc"));
            atts.add(new Attribute("smoke"));
            atts.add(new Attribute("alco"));
            atts.add(new Attribute("active"));
            atts.add(new Attribute("cardio"));

            Instances data = new Instances("CardioTrain", atts, 0);
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM dataset_cardio");
            while(rs.next()) {
                double[] vals = new double[data.numAttributes()];
                vals[0] = rs.getInt("age_days");
                vals[1] = rs.getInt("gender");
                vals[2] = rs.getInt("height");
                vals[3] = rs.getDouble("weight");
                vals[4] = rs.getInt("ap_hi");
                vals[5] = rs.getInt("ap_lo");
                vals[6] = rs.getInt("cholesterol");
                vals[7] = rs.getInt("gluc");
                vals[8] = rs.getInt("smoke");
                vals[9] = rs.getInt("alco");
                vals[10] = rs.getInt("active");
                vals[11] = rs.getInt("cardio");
                data.add(new DenseInstance(1.0, vals));
            }
            processTraining(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processTraining(Instances data) throws Exception {
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        CorrelationAttributeEval eval = null;
        boolean wekaAvailable = false;
        
        try {
            eval = new CorrelationAttributeEval();
            eval.buildEvaluator(data);
            wekaAvailable = true;
        } catch (Throwable t) { 
        }

        try (Connection conn = DBConnect.getConnection()) {
            Statement stmt = conn.createStatement();
            
            String upsert = "MERGE INTO fuzzy_stats_v2 (attribute_name, mean_val, std_dev, weight_factor) KEY(attribute_name) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(upsert);

            for (int i = 0; i < data.numAttributes(); i++) {
                if (i == data.classIndex()) continue;

                String attrName = data.attribute(i).name();
                double mean = 0.0;
                double std = 1.0;
                double weight = 0.0;

                if (wekaAvailable && eval != null) {
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
}