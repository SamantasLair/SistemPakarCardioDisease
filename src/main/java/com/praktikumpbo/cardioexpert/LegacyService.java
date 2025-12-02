package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

public class LegacyService {

    public static String j48Rules = "Model Legacy J48 belum dilatih.";

    public static void loadLegacyModel() {
        try (Connection conn = DBConnect.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM ai_models WHERE model_type='J48' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                j48Rules = rs.getString("rules_text");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void trainJ48(Instances dataRaw) {
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
            sb.append("=== LEGACY J48 DECISION TREE (EASTER EGG) ===\n");
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
            j48Rules = "Training Failed: " + t.getMessage();
        }
    }
}