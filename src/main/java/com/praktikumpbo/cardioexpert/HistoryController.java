package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class HistoryController {

    @FXML private TableView<HistoryData> tableHistory;
    @FXML private TableColumn<HistoryData, String> colDate;
    @FXML private TableColumn<HistoryData, String> colScore;
    @FXML private TableColumn<HistoryData, String> colLevel;
    @FXML private TableColumn<HistoryData, String> colRec;

    public void initialize() {
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().date));
        colScore.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().score));
        colLevel.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().level));
        colRec.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().recommendation));

        loadData();
    }

    private void loadData() {
        ObservableList<HistoryData> list = FXCollections.observableArrayList();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT consult_date, risk_score, risk_level, recommendation FROM consultations WHERE user_id = ? ORDER BY consult_date DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, UserSession.id);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new HistoryData(
                    rs.getString("consult_date"),
                    String.format("%.1f %%", rs.getDouble("risk_score")),
                    rs.getString("risk_level"),
                    rs.getString("recommendation")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        tableHistory.setItems(list);
    }

    public static class HistoryData {
        String date, score, level, recommendation;

        public HistoryData(String date, String score, String level, String recommendation) {
            this.date = date;
            this.score = score;
            this.level = level;
            this.recommendation = recommendation;
        }
    }
}