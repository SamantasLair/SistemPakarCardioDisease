package com.praktikumpbo.cardioexpert;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

public class SplashController {
    
    @FXML private VBox splashContainer;
    @FXML private Label lblStatus;
    @FXML private ProgressBar progressBar;

    @FXML
    public void initialize() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> lblStatus.setText("Memuat konfigurasi sistem..."));
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    lblStatus.setText("Menghubungkan ke database...");
                    progressBar.setProgress(0.5);
                });
                
                DBConnect.initializeDatabase();
                Thread.sleep(1000);

                Platform.runLater(() -> {
                    lblStatus.setText("Siap...");
                    progressBar.setProgress(1.0);
                });
                Thread.sleep(500);

                Platform.runLater(() -> {
                    try {
                        App.setRoot("welcome");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}