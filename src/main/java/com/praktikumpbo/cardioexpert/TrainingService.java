package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.core.AttributeStats;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

public class TrainingService {

    public static void trainSystem(String csvPath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        loader.setFieldSeparator(";");
        Instances data = loader.getDataSet();
        
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        calculateStatisticsAndWeights(data);
    }

    private static void calculateStatisticsAndWeights(Instances data) throws Exception {
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
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fuzzy_stats_v2 (attribute_name VARCHAR(50) PRIMARY KEY, mean_val DOUBLE, std_dev DOUBLE, weight_factor DOUBLE)");

            String upsert = "INSERT INTO fuzzy_stats_v2 VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE mean_val=?, std_dev=?, weight_factor=?";
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
}