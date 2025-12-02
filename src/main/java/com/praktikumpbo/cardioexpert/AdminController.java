package com.praktikumpbo.cardioexpert;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @FXML private Label lblTotalData, lblHighRisk, lblRiskPercent;
    @FXML private BarChart<Number, String> chartRiskDist; 
    @FXML private BarChart<String, Number> barChartAge;
    @FXML private LineChart<Number, Number> chartFuzzyBP, chartFuzzyAge, chartFuzzyWeight;
    
    @FXML private Label lblTP, lblTN, lblFP, lblFN;
    @FXML private Label lblAccuracy, lblPrecision, lblRecall, lblF1;

    @FXML private VBox drawer;
    @FXML private VBox sidebar;

    private boolean isDrawerOpen = false;
    private boolean isSidebarOpen = false;
    private final double SIDEBAR_WIDTH = 250.0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        logger.info("[ADMIN] Membuka halaman Dashboard Admin.");
        sidebar.setTranslateX(-SIDEBAR_WIDTH);
        
        try {
            InferenceEngine.loadSystem();
            loadAnalytics();
            plotFuzzyGraphs();
        } catch (Exception e) {
            logger.error("[ADMIN] Error saat inisialisasi Dashboard", e);
        }
    }

    @FXML
    private void toggleSidebar() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), sidebar);
        if (isSidebarOpen) transition.setToX(-SIDEBAR_WIDTH);
        else transition.setToX(0);
        transition.play();
        isSidebarOpen = !isSidebarOpen;
    }
    
    private void closeSidebar() {
        if (isSidebarOpen) {
            TranslateTransition transition = new TranslateTransition(Duration.millis(300), sidebar);
            transition.setToX(-SIDEBAR_WIDTH);
            transition.play();
            isSidebarOpen = false;
        }
    }

    @FXML private void handleDashboardOverview() { closeSidebar(); }
    @FXML private void handleOutsideClick() { if (isSidebarOpen) closeSidebar(); }

    @FXML
    private void toggleDrawer() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawer);
        if (isDrawerOpen) transition.setToX(500); 
        else {
            transition.setToX(0);
            calculateMatrix(); 
        }
        transition.play();
        isDrawerOpen = !isDrawerOpen;
    }

    private void calculateMatrix() {
        lblAccuracy.setText("Menghitung...");
        Task<EvaluationService.MatrixResult> task = new Task<>() {
            @Override
            protected EvaluationService.MatrixResult call() throws Exception {
                return EvaluationService.evaluateModel();
            }
        };

        task.setOnSucceeded(e -> {
            EvaluationService.MatrixResult res = task.getValue();
            lblTP.setText(String.valueOf(res.tp));
            lblTN.setText(String.valueOf(res.tn));
            lblFP.setText(String.valueOf(res.fp));
            lblFN.setText(String.valueOf(res.fn));
            
            lblAccuracy.setText(String.format("%.1f%%", res.accuracy * 100));
            lblPrecision.setText(String.format("%.1f%%", res.precision * 100));
            lblRecall.setText(String.format("%.1f%%", res.recall * 100));
            lblF1.setText(String.format("%.1f%%", res.f1 * 100));
        });
        
        new Thread(task).start();
    }

    @FXML
    private void handleImportCsv() {
        closeSidebar();
        FileChooser fc = new FileChooser();
        fc.setTitle("Pilih File CSV");
        File file = fc.showOpenDialog(null);
        if (file != null) {
            logger.info("[ADMIN] User memilih file CSV: {}", file.getAbsolutePath());
            CsvLoader.loadCsvToDb(file.getAbsolutePath());
            try {
                logger.info("[ADMIN] Memulai training ulang sistem...");
                TrainingService.trainSystem(file.getAbsolutePath());
                InferenceEngine.loadSystem();
                loadAnalytics();
                plotFuzzyGraphs();
                new Alert(Alert.AlertType.INFORMATION, "Import dan Training Berhasil!").show();
            } catch (Exception e) {
                logger.error("[ADMIN] Pelatihan Gagal", e);
                new Alert(Alert.AlertType.ERROR, "Gagal: " + e.getMessage()).show();
            }
        }
    }

    @FXML
    private void handleResetData() {
        closeSidebar();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Konfirmasi Reset");
        alert.setHeaderText("Hapus Semua Data?");
        alert.setContentText("Tindakan ini akan menghapus seluruh dataset dan statistik.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = DBConnect.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.executeUpdate("TRUNCATE TABLE dataset_cardio");
                stmt.executeUpdate("TRUNCATE TABLE fuzzy_stats_v2");
                
                InferenceEngine.means.clear();
                InferenceEngine.stdDevs.clear();
                InferenceEngine.weights.clear();
                
                lblTotalData.setText("0");
                lblHighRisk.setText("0");
                lblRiskPercent.setText("0%");
                chartRiskDist.getData().clear();
                barChartAge.getData().clear();
                chartFuzzyBP.getData().clear();
                chartFuzzyAge.getData().clear();
                chartFuzzyWeight.getData().clear();
                
                new Alert(Alert.AlertType.INFORMATION, "Sistem berhasil direset.").show();
                
            } catch (Exception e) {
                logger.error("[ADMIN] Gagal Reset", e);
                new Alert(Alert.AlertType.ERROR, "Gagal reset: " + e.getMessage()).show();
            }
        }
    }

    @FXML private void handleSimulation() throws IOException { App.setRoot("patient"); }

    private void loadAnalytics() {
        try (Connection conn = DBConnect.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*), SUM(CASE WHEN cardio=1 THEN 1 ELSE 0 END) FROM dataset_cardio");
            int total = 0;
            int highRisk = 0;
            if (rs.next()) {
                total = rs.getInt(1);
                highRisk = rs.getInt(2);
                lblTotalData.setText(String.format("%,d", total));
                lblHighRisk.setText(String.format("%,d", highRisk));
            }

            double percent = (total > 0) ? ((double)highRisk / total * 100) : 0;
            lblRiskPercent.setText(String.format("%.1f%%", percent));

            rs = stmt.executeQuery("SELECT cardio, COUNT(*) FROM dataset_cardio GROUP BY cardio");
            XYChart.Series<Number, String> seriesRisk = new XYChart.Series<>();
            seriesRisk.setName("Jumlah Pasien");
            
            int healthyCount = 0;
            int sickCount = 0;
            while (rs.next()) {
                if (rs.getInt(1) == 0) healthyCount = rs.getInt(2);
                else sickCount = rs.getInt(2);
            }
            seriesRisk.getData().add(new XYChart.Data<>(sickCount, "Berisiko"));
            seriesRisk.getData().add(new XYChart.Data<>(healthyCount, "Sehat"));
            
            chartRiskDist.getData().clear();
            chartRiskDist.getData().add(seriesRisk);

            rs = stmt.executeQuery("SELECT FLOOR(age_days/365/10)*10 as d, SUM(cardio) FROM dataset_cardio GROUP BY d ORDER BY d");
            XYChart.Series<String, Number> seriesAge = new XYChart.Series<>();
            seriesAge.setName("Kasus");
            while (rs.next()) {
                seriesAge.getData().add(new XYChart.Data<>(rs.getString(1) + "s", rs.getInt(2)));
            }
            barChartAge.getData().clear();
            barChartAge.getData().add(seriesAge);

        } catch (Exception e) {
            logger.error("[ADMIN] Gagal memuat analitik", e);
        }
    }

    private void plotFuzzyGraphs() {
        if (InferenceEngine.means.isEmpty()) return;
        
        plotGaussian(chartFuzzyBP, InferenceEngine.means.get("ap_hi"), InferenceEngine.stdDevs.get("ap_hi"));
        
        String ageKey = InferenceEngine.means.containsKey("age") ? "age" : "age_days";
        double meanAge = InferenceEngine.means.getOrDefault(ageKey, 0.0);
        double stdAge = InferenceEngine.stdDevs.getOrDefault(ageKey, 1.0);
        
        if (meanAge > 200) { meanAge /= 365.0; stdAge /= 365.0; }
        
        plotGaussian(chartFuzzyAge, meanAge, stdAge);
        plotGaussian(chartFuzzyWeight, InferenceEngine.means.get("weight"), InferenceEngine.stdDevs.get("weight"));
    }

    private void plotGaussian(LineChart<Number, Number> chart, Double mean, Double std) {
        if (mean == null || std == null) return;
        
        chart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Distribusi Normal");

        double minX = mean - 3.5 * std;
        double maxX = mean + 3.5 * std;
        double step = (maxX - minX) / 50;

        for (double x = minX; x <= maxX; x += step) {
            double mu = Math.exp(-0.5 * Math.pow((x - mean) / std, 2));
            series.getData().add(new XYChart.Data<>(x, mu));
        }
        chart.getData().add(series);
    }
    
    @FXML private void handleLogout() throws java.io.IOException { App.setRoot("login"); }
}