package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class RegisterController {
    @FXML private TextField inName;
    @FXML private TextField inUser;
    @FXML private PasswordField inPass;
    @FXML private VBox registerCard;

    @FXML
    public void initialize() {
        if (registerCard != null) {
            registerCard.setOpacity(0);
            registerCard.setTranslateY(30);

            TranslateTransition tt = new TranslateTransition(Duration.millis(700), registerCard);
            tt.setToY(0);
            
            FadeTransition ft = new FadeTransition(Duration.millis(700), registerCard);
            ft.setToValue(1);

            tt.play();
            ft.play();
        }
    }

    @FXML
    private void handleRegister() {
        if (inName.getText().isEmpty() || inUser.getText().isEmpty() || inPass.getText().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Mohon lengkapi semua data").show();
            return;
        }

        try (Connection conn = DBConnect.getConnection()) {
            String sql = "INSERT INTO users (id, username, password, fullname, role) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, inUser.getText());
            ps.setString(3, inPass.getText());
            ps.setString(4, inName.getText());
            ps.setString(5, "PATIENT");
            
            ps.executeUpdate();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Registrasi Berhasil! Silakan Login.");
            alert.showAndWait();
            App.setRoot("login");

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Gagal Mendaftar: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() throws java.io.IOException {
        App.setRoot("login");
    }
}