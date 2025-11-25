package com.praktikumpbo.cardioexpert;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javafx.animation.TranslateTransition;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javax.imageio.ImageIO;

public class PatientController {

    @FXML private TextField inAge, inHeight, inWeight, inApHi, inApLo;
    @FXML private ComboBox<String> cbChol, cbGluc;
    @FXML private CheckBox chkSmoke, chkActive, chkAlco;
    @FXML private RadioButton rbMale;
    @FXML private VBox resultBox, drawer;
    @FXML private Label lblResultLevel;
    @FXML private TextArea txtRecommendation, txtLog, txtRulesDesc;
    @FXML private Button btnBackAdmin;
    @FXML private LineChart<Number, Number> chartFuzzyBP, chartFuzzyAge, chartFuzzyWeight;

    private boolean isDrawerOpen = false;
    private String currentLog = "";

    @FXML
    public void initialize() {
        cbChol.getItems().addAll("Normal", "Di Atas Normal", "Sangat Tinggi");
        cbGluc.getItems().addAll("Normal", "Di Atas Normal", "Sangat Tinggi");
        cbChol.getSelectionModel().selectFirst();
        cbGluc.getSelectionModel().selectFirst();
        
        txtRulesDesc.setText(InferenceEngine.getFuzzyRulesDocumentation());

        if ("EXPERT".equals(UserSession.role)) {
            btnBackAdmin.setVisible(true);
            btnBackAdmin.setManaged(true);
        } else {
            btnBackAdmin.setVisible(false);
            btnBackAdmin.setManaged(false);
        }
    }

    @FXML
    private void toggleDrawer() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawer);
        if (isDrawerOpen) transition.setToX(600); 
        else transition.setToX(0); 
        transition.play();
        isDrawerOpen = !isDrawerOpen;
    }

    @FXML
    private void handleBackAdmin() throws java.io.IOException {
        App.setRoot("admin");
    }

    @FXML
    private void handleDiagnose() {
        try {
            int age = Integer.parseInt(inAge.getText());
            int h = Integer.parseInt(inHeight.getText());
            double w = Double.parseDouble(inWeight.getText());
            int hi = Integer.parseInt(inApHi.getText());
            int lo = Integer.parseInt(inApLo.getText());
            int chol = cbChol.getSelectionModel().getSelectedIndex() + 1;
            int gluc = cbGluc.getSelectionModel().getSelectedIndex() + 1;
            int smoke = chkSmoke.isSelected() ? 1 : 0;
            int alco = chkAlco.isSelected() ? 1 : 0;
            int active = chkActive.isSelected() ? 1 : 0;
            int gender = (rbMale != null && rbMale.isSelected()) ? 2 : 1; 
            
            double heightM = h / 100.0;
            double bmi = w / (heightM * heightM);

            var result = InferenceEngine.predictFuzzy(age, gender, h, w, hi, lo, chol, gluc, smoke, alco, active);
            currentLog = result.calculationLog;

            resultBox.setVisible(true);
            
            lblResultLevel.setText(result.level + " (" + String.format("%.1f", result.score) + "%)");
            txtRecommendation.setText(result.recommendation);
            txtLog.setText(result.calculationLog);
            
            if (result.level.contains("TINGGI")) 
                lblResultLevel.setStyle("-fx-text-fill: #d63031; -fx-font-size: 24px; -fx-font-weight: bold;");
            else if (result.level.contains("SEDANG")) 
                lblResultLevel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 24px; -fx-font-weight: bold;");
            else 
                lblResultLevel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 24px; -fx-font-weight: bold;");

            saveHistory(age, bmi, hi, lo, chol, result);
            plotPatientPosition(age, hi, w);

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Mohon masukkan angka yang valid!").show();
        }
    }

    private void plotPatientPosition(int age, int bp, double weight) {
        if (InferenceEngine.means.containsKey("ap_hi")) {
            plotGaussianWithLine(chartFuzzyBP, 
                InferenceEngine.means.get("ap_hi"), 
                InferenceEngine.stdDevs.get("ap_hi"), 
                bp);
        }

        if (InferenceEngine.means.containsKey("weight")) {
            plotGaussianWithLine(chartFuzzyWeight, 
                InferenceEngine.means.get("weight"), 
                InferenceEngine.stdDevs.get("weight"), 
                weight);
        }

        String ageKey = InferenceEngine.means.containsKey("age") ? "age" : "age_days";
        if (InferenceEngine.means.containsKey(ageKey)) {
            double meanDays = InferenceEngine.means.get(ageKey);
            double stdDays = InferenceEngine.stdDevs.get(ageKey);
            plotGaussianWithLine(chartFuzzyAge, meanDays / 365.0, stdDays / 365.0, age);
        }
    }

    private void plotGaussianWithLine(LineChart<Number, Number> chart, double mean, double std, double patientVal) {
        chart.getData().clear();
        
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Populasi");
        
        double minX = mean - 3.5 * std;
        double maxX = mean + 3.5 * std;
        double step = (maxX - minX) / 100;
        double peakY = (1 / (std * Math.sqrt(2 * Math.PI)));

        for (double x = minX; x <= maxX; x += step) {
            double y = (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));
            series.getData().add(new XYChart.Data<>(x, y));
        }

        XYChart.Series<Number, Number> patientLine = new XYChart.Series<>();
        patientLine.setName("Anda");
        patientLine.getData().add(new XYChart.Data<>(patientVal, 0));
        patientLine.getData().add(new XYChart.Data<>(patientVal, peakY));

        chart.getData().addAll(series, patientLine);
    }

    private void saveHistory(int age, double bmi, int hi, int lo, int chol, InferenceEngine.DiagnosisResult res) {
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "INSERT INTO consultations (id, user_id, age_years, bmi, ap_hi, ap_lo, cholesterol, risk_score, risk_level, recommendation) VALUES (?,?,?,?,?,?,?,?,?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, UserSession.id);
            ps.setInt(3, age);
            ps.setDouble(4, bmi);
            ps.setInt(5, hi);
            ps.setInt(6, lo);
            ps.setInt(7, chol);
            ps.setDouble(8, res.score);
            ps.setString(9, res.level);
            ps.setString(10, res.recommendation);
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    @FXML
    private void handleExportPdf() {
        try {
            String fname = "Laporan_" + System.currentTimeMillis() + ".pdf";
            PdfWriter writer = new PdfWriter(fname);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            
            doc.add(new Paragraph("LAPORAN ANALISIS JANTUNG").setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("------------------------------------------------"));
            doc.add(new Paragraph("Nama Pasien: " + UserSession.name));
            doc.add(new Paragraph("Hasil Analisis: " + lblResultLevel.getText()).setBold());
            doc.add(new Paragraph("Rekomendasi: \n" + txtRecommendation.getText()));
            doc.add(new AreaBreak());
            doc.add(new Paragraph("LOGIKA MATEMATIS (DATA DRIVEN):").setBold());
            doc.add(new Paragraph(currentLog).setFontSize(10));
            
            doc.close();
            new Alert(Alert.AlertType.INFORMATION, "PDF Tersimpan: " + fname).show();
        } catch (Exception e) {}
    }

    @FXML
    private void handleExportImage() {
        try {
            WritableImage image = resultBox.snapshot(new SnapshotParameters(), null);
            File file = new File("Laporan_" + System.currentTimeMillis() + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            new Alert(Alert.AlertType.INFORMATION, "Gambar Tersimpan: " + file.getName()).show();
        } catch (Exception e) {}
    }
    
    @FXML private void handleLogout() throws java.io.IOException { App.setRoot("login"); }
}