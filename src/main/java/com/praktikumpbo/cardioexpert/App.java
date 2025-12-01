package com.praktikumpbo.cardioexpert;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.IOException;

public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("welcome"), 1024, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/styles.css").toExternalForm());
        stage.setTitle("CardioExpert - Medical System");
        stage.setScene(scene);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        Parent root = loadFXML(fxml);
        
        // Animasi Fade In Global saat ganti halaman
        root.setOpacity(0);
        scene.setRoot(root);
        
        FadeTransition ft = new FadeTransition(Duration.millis(600), root);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();

        Stage stage = (Stage) scene.getWindow();
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    public static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/fxml/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}