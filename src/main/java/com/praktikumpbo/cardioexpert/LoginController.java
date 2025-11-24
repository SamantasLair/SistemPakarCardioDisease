package com.praktikumpbo.cardioexpert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {
    @FXML private TextField inUser;
    @FXML private PasswordField inPass;

    @FXML
    private void handleLogin() {
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT id, role, fullname FROM users WHERE username=? AND password=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, inUser.getText());
            ps.setString(2, inPass.getText());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                UserSession.id = rs.getString("id");
                UserSession.role = rs.getString("role");
                UserSession.name = rs.getString("fullname");

                if ("EXPERT".equals(UserSession.role)) App.setRoot("admin");
                else App.setRoot("patient");
            } else {
                new Alert(Alert.AlertType.ERROR, "Username atau Password Salah").show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}