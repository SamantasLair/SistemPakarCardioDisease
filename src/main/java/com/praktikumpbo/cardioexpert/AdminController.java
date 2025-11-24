package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ResourceBundle;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public class AdminController implements Initializable {

    @FXML private Label lblTotalData, lblHighRisk;
    @FXML private PieChart pieChart;
    @FXML private BarChart<String, Number> barChart;
    @FXML private LineChart<Number, Number> chartFuzzyBP, chartFuzzyAge, chartFuzzyWeight;
    @FXML private TextArea txtTree;
    @FXML private VBox drawer;

    private boolean isDrawerOpen = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        InferenceEngine.loadSystem();
        txtTree.setText(InferenceEngine.j48Rules);
        loadAnalytics();
        plotFuzzyGraphs();
    }

    @FXML
    private void toggleDrawer() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawer);
        if (isDrawerOpen) transition.setToX(500); 
        else transition.setToX(0); 
        transition.play();
        isDrawerOpen = !isDrawerOpen;
    }

    @FXML
    private void handleImportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Pilih File CSV");
        File file = fc.showOpenDialog(null);
        if (file != null) {
            CsvLoader.loadCsvToDb(file.getAbsolutePath());
            try {
                InferenceEngine.trainSystem(file.getAbsolutePath());
                txtTree.setText(InferenceEngine.j48Rules);
                loadAnalytics();
                plotFuzzyGraphs();
            } catch (Exception e) {
                txtTree.setText("Pelatihan Gagal: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSimulation() throws IOException {
        App.setRoot("patient");
    }

    private void loadAnalytics() {
        try (Connection conn = DBConnect.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*), SUM(CASE WHEN cardio=1 THEN 1 ELSE 0 END) FROM dataset_cardio");
            if (rs.next()) {
                lblTotalData.setText(rs.getString(1));
                lblHighRisk.setText(rs.getString(2));
            }

            rs = stmt.executeQuery("SELECT cardio, COUNT(*) FROM dataset_cardio GROUP BY cardio");
            var pieData = FXCollections.<PieChart.Data>observableArrayList();
            while (rs.next()) pieData.add(new PieChart.Data(rs.getInt(1)==1?"Sakit":"Sehat", rs.getInt(2)));
            pieChart.setData(pieData);

            rs = stmt.executeQuery("SELECT FLOOR(age_days/365/10)*10 as d, SUM(cardio) FROM dataset_cardio GROUP BY d");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            while (rs.next()) series.getData().add(new XYChart.Data<>(rs.getString(1)+"an", rs.getInt(2)));
            barChart.getData().clear();
            barChart.getData().add(series);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void plotFuzzyGraphs() {
        if (InferenceEngine.means.isEmpty()) return;
        plotGaussian(chartFuzzyBP, "ap_hi", InferenceEngine.means.get("ap_hi"), InferenceEngine.stdDevs.get("ap_hi"));
        plotGaussian(chartFuzzyAge, "age_days", InferenceEngine.means.get("age_days"), InferenceEngine.stdDevs.get("age_days"));
        plotGaussian(chartFuzzyWeight, "weight", InferenceEngine.means.get("weight"), InferenceEngine.stdDevs.get("weight"));
    }

    private void plotGaussian(LineChart<Number, Number> chart, String name, double mean, double std) {
        chart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Distribusi Populasi");

        double minX = mean - 3 * std;
        double maxX = mean + 3 * std;
        double step = (maxX - minX) / 50;

        for (double x = minX; x <= maxX; x += step) {
            double y = (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));
            series.getData().add(new XYChart.Data<>(x, y));
        }
        chart.getData().add(series);
    }
    
    @FXML private void handleLogout() throws java.io.IOException { App.setRoot("login"); }
}