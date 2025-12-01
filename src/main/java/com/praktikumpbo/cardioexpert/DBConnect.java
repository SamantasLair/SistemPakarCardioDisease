package com.praktikumpbo.cardioexpert;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DBConnect {
    private static Connection conn;

    public static Connection getConnection() {
        try {
            if (conn == null || conn.isClosed()) {
                Properties props = new Properties();
                try (InputStream input = DBConnect.class.getResourceAsStream("/config.properties")) {
                    props.load(input);
                }
                conn = DriverManager.getConnection(
                    props.getProperty("db.url"), 
                    props.getProperty("db.user"), 
                    props.getProperty("db.password")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return conn;
    }
}