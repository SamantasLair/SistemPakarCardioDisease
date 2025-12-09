package com.praktikumpbo.cardioexpert;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
    @FXML private Button btnBackAdmin, btnHistory, btnLogout, btnDiagnose;
    @FXML private LineChart<Number, Number> chartFuzzyBP, chartFuzzyAge, chartFuzzyWeight;
    @FXML private HBox rootBox; 

    private boolean isDrawerOpen = false;
    private String currentCalcLog = "";

    @FXML
    public void initialize() {
        if (rootBox != null) {
            rootBox.setOpacity(0);
            rootBox.setTranslateY(20);
            TranslateTransition tt = new TranslateTransition(Duration.millis(600), rootBox);
            tt.setToY(0);
            FadeTransition ft = new FadeTransition(Duration.millis(600), rootBox);
            ft.setToValue(1);
            tt.play();
            ft.play();
        }

        cbChol.getItems().addAll("Normal(< 100 mg/dL)", "Di Atas Normal(100 – 125 mg/dL)", "Sangat Tinggi(≥ 126 mg/dL)");
        cbGluc.getItems().addAll("Normal(< 200 mg/dL)", "Di Atas Normal(200 – 239 mg/dL)", "Sangat Tinggi(≥ 240 mg/dL)");
        cbChol.getSelectionModel().selectFirst();
        cbGluc.getSelectionModel().selectFirst();
        
        txtRulesDesc.setText(InferenceEngine.getFuzzyRulesDocumentation());

        if (UserSession.isGuest()) {
            btnBackAdmin.setVisible(false);
            btnBackAdmin.setManaged(false);
            btnHistory.setVisible(false); 
            btnHistory.setManaged(false);
            btnLogout.setText("Kembali ke Depan");
        } else if ("EXPERT".equals(UserSession.role)) {
            btnBackAdmin.setVisible(true);
            btnBackAdmin.setManaged(true);
            btnHistory.setVisible(false);
        } else {
            btnBackAdmin.setVisible(false);
            btnBackAdmin.setManaged(false);
            btnHistory.setVisible(true);
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
    private void handleBackAdmin() throws IOException {
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

            if (age < 1 || age > 120) throw new IllegalArgumentException("Umur tidak valid (1-120)");
            if (h < 50 || h > 250) throw new IllegalArgumentException("Tinggi badan tidak valid");
            if (w < 10 || w > 300) throw new IllegalArgumentException("Berat badan tidak valid");
            if (hi < 50 || hi > 250) throw new IllegalArgumentException("Tensi Sistolik tidak valid");
            if (lo < 30 || lo > 200) throw new IllegalArgumentException("Tensi Diastolik tidak valid");

            int chol = cbChol.getSelectionModel().getSelectedIndex() + 1;
            int gluc = cbGluc.getSelectionModel().getSelectedIndex() + 1;
            int smoke = chkSmoke.isSelected() ? 1 : 0;
            int alco = chkAlco.isSelected() ? 1 : 0;
            int active = chkActive.isSelected() ? 1 : 0;
            int gender = (rbMale != null && rbMale.isSelected()) ? 2 : 1; 
            
            double heightM = h / 100.0;
            double bmi = w / (heightM * heightM);

            btnDiagnose.setDisable(true);
            btnDiagnose.setText("Menganalisis...");

            Task<InferenceEngine.DiagnosisResult> task = new Task<>() {
                @Override
                protected InferenceEngine.DiagnosisResult call() throws Exception {
                    InferenceEngine.DiagnosisResult res = InferenceEngine.predictSugeno(age, gender, h, w, hi, lo, chol, gluc, smoke, alco, active);
                    if (!UserSession.isGuest()) {
                        saveHistory(age, bmi, hi, lo, chol, res);
                    }
                    return res;
                }
            };

            task.setOnSucceeded(e -> {
                InferenceEngine.DiagnosisResult result = task.getValue();
                currentCalcLog = result.calcLog;
                
                resultBox.setVisible(true);
                resultBox.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(500), resultBox);
                ft.setToValue(1);
                ft.play();
                
                lblResultLevel.setText(result.level + " (" + String.format("%.1f", result.score) + "%)");
                txtRecommendation.setText(result.recommendation);
                txtLog.setText(result.calcLog);
                txtRulesDesc.setText(InferenceEngine.getFuzzyRulesDocumentation() + "\n\n=== ATURAN AKTIF ===\n" + result.rulesActive);
                
                if (result.level.contains("TINGGI")) 
                    lblResultLevel.setStyle("-fx-text-fill: #d63031; -fx-font-size: 24px; -fx-font-weight: bold;");
                else if (result.level.contains("SEDANG")) 
                    lblResultLevel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 24px; -fx-font-weight: bold;");
                else 
                    lblResultLevel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 24px; -fx-font-weight: bold;");

                plotFuzzyCharts(age, hi, w);
                btnDiagnose.setDisable(false);
                btnDiagnose.setText("ANALISIS SISTEM PAKAR");
            });

            task.setOnFailed(e -> {
                btnDiagnose.setDisable(false);
                btnDiagnose.setText("ANALISIS SISTEM PAKAR");
                new Alert(Alert.AlertType.ERROR, "Gagal koneksi database atau error pada sistem inferensi.").show();
                e.getSource().getException().printStackTrace();
            });

            new Thread(task).start();

        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Mohon masukkan angka yang valid!").show();
        } catch (IllegalArgumentException e) {
            new Alert(Alert.AlertType.WARNING, e.getMessage()).show();
        }
    }
    
    @FXML
    private void handleShowHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Riwayat Konsultasi - " + UserSession.name);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void plotFuzzyCharts(int age, int bp, double weight) {
        if (InferenceEngine.means.containsKey("ap_hi")) {
            plotMembershipFunction(chartFuzzyBP, InferenceEngine.means.get("ap_hi"), InferenceEngine.stdDevs.get("ap_hi"), bp);
        }
        if (InferenceEngine.means.containsKey("weight")) {
            plotMembershipFunction(chartFuzzyWeight, InferenceEngine.means.get("weight"), InferenceEngine.stdDevs.get("weight"), weight);
        }
        String ageKey = InferenceEngine.means.containsKey("age") ? "age" : "age_days";
        if (InferenceEngine.means.containsKey(ageKey)) {
            double meanDays = InferenceEngine.means.get(ageKey);
            double stdDays = InferenceEngine.stdDevs.get(ageKey);
            plotMembershipFunction(chartFuzzyAge, meanDays / 365.0, stdDays / 365.0, age);
        }
    }

    private void plotMembershipFunction(LineChart<Number, Number> chart, double mean, double std, double patientVal) {
        chart.getData().clear();
        XYChart.Series<Number, Number> seriesMu = new XYChart.Series<>();
        seriesMu.setName("Kurva Normal");
        double minX = mean - 3.5 * std;
        double maxX = mean + 3.5 * std;
        double step = (maxX - minX) / 100;
        for (double x = minX; x <= maxX; x += step) {
            double mu = Math.exp(-0.5 * Math.pow((x - mean) / std, 2));
            seriesMu.getData().add(new XYChart.Data<>(x, mu));
        }
        XYChart.Series<Number, Number> patientLine = new XYChart.Series<>();
        patientLine.setName("Posisi Pasien");
        double patientMu = Math.exp(-0.5 * Math.pow((patientVal - mean) / std, 2));
        patientLine.getData().add(new XYChart.Data<>(patientVal, 0));
        patientLine.getData().add(new XYChart.Data<>(patientVal, patientMu));
        chart.getData().addAll(seriesMu, patientLine);
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleExportPdf() {
        try {
            String fname = "Laporan_Expert_" + System.currentTimeMillis() + ".pdf";
            PdfWriter writer = new PdfWriter(fname);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            
            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontMono = PdfFontFactory.createFont(StandardFonts.COURIER);

            doc.add(new Paragraph("LAPORAN DIAGNOSIS JANTUNG (EXPERT SYSTEM)")
                    .setFont(fontBold).setFontSize(18).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("------------------------------------------------"));
            doc.add(new Paragraph("Nama Pasien: " + UserSession.name));
            doc.add(new Paragraph("Tingkat Risiko: " + lblResultLevel.getText()).setFont(fontBold));
            doc.add(new Paragraph("Rekomendasi: \n" + txtRecommendation.getText()));
            doc.add(new AreaBreak());
            doc.add(new Paragraph("DETAIL PERHITUNGAN (HYBRID FUZZY + CRISP):").setFont(fontBold));
            doc.add(new Paragraph(currentCalcLog).setFontSize(9).setFont(fontMono));
            doc.close();
            new Alert(Alert.AlertType.INFORMATION, "PDF Laporan Tersimpan: " + fname).show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Gagal ekspor PDF: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }

    @FXML
    private void handleExportImage() {
        try {
            WritableImage image = resultBox.snapshot(new SnapshotParameters(), null);
            File file = new File("Result_Expert_" + System.currentTimeMillis() + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            new Alert(Alert.AlertType.INFORMATION, "Gambar Tersimpan: " + file.getName()).show();
        } catch (Exception e) {
             new Alert(Alert.AlertType.ERROR, "Gagal simpan gambar: " + e.getMessage()).show();
        }
    }
    
    @FXML 
    private void handleLogout() throws java.io.IOException { 
        UserSession.clear();
        App.setRoot("welcome"); 
    }
}