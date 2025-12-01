package com.praktikumpbo.cardioexpert;

import java.io.IOException;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class WelcomeController {

    @FXML private VBox splashContainer;
    @FXML private VBox menuContainer;

    @FXML
    public void initialize() {
        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        delay.setOnFinished(event -> startTransition());
        delay.play();
    }

    private void startTransition() {
        FadeTransition fadeOutSplash = new FadeTransition(Duration.seconds(0.8), splashContainer);
        fadeOutSplash.setFromValue(1.0);
        fadeOutSplash.setToValue(0.0);
        fadeOutSplash.setOnFinished(e -> {
            splashContainer.setVisible(false);
            showMenu();
        });
        fadeOutSplash.play();
    }

    private void showMenu() {
        menuContainer.setVisible(true);
        menuContainer.setOpacity(0);
        menuContainer.setTranslateY(20);

        FadeTransition fadeInMenu = new FadeTransition(Duration.seconds(0.8), menuContainer);
        fadeInMenu.setToValue(1.0);

        TranslateTransition slideUpMenu = new TranslateTransition(Duration.seconds(0.8), menuContainer);
        slideUpMenu.setToY(0);

        fadeInMenu.play();
        slideUpMenu.play();
    }

    @FXML
    private void handleGuest() throws IOException {
        UserSession.clear();
        UserSession.name = "Tamu (Guest)";
        UserSession.role = "GUEST";
        App.setRoot("patient");
    }

    @FXML
    private void handleLogin() throws IOException {
        App.setRoot("login");
    }
}