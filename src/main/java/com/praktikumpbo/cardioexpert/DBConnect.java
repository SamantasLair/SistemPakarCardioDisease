package com.praktikumpbo.cardioexpert;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBConnect {
    private static Connection conn;
    private static final Logger logger = LoggerFactory.getLogger(DBConnect.class);

    public static Connection getConnection() {
        try {
            if (conn == null || conn.isClosed()) {
                Properties props = new Properties();
                try (InputStream input = DBConnect.class.getResourceAsStream("/config.properties")) {
                    props.load(input);
                }
                Class.forName("org.h2.Driver");
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

    public static void initializeDatabase() {
        try (Connection c = getConnection(); Statement stmt = c.createStatement()) {
            
            // Perbaikan urutan Syntax SQL di tabel model_evaluation
            String[] tables = {
                "CREATE TABLE IF NOT EXISTS users (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(50) NOT NULL UNIQUE, " +
                "password VARCHAR(255) NOT NULL, " +
                "fullname VARCHAR(100), " +
                "role VARCHAR(20) NOT NULL)",
                
                "CREATE TABLE IF NOT EXISTS dataset_cardio (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "age_days INT, gender INT, height INT, weight DOUBLE, " +
                "ap_hi INT, ap_lo INT, cholesterol INT, gluc INT, " +
                "smoke INT, alco INT, active INT, cardio INT)",
                
                "CREATE TABLE IF NOT EXISTS consultations (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "user_id VARCHAR(36), " +
                "age_years INT, bmi DOUBLE, ap_hi INT, ap_lo INT, cholesterol INT, " +
                "risk_score DOUBLE, risk_level VARCHAR(20), recommendation TEXT, " +
                "consult_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id))",
                
                "CREATE TABLE IF NOT EXISTS ai_models (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "model_type VARCHAR(20), model_blob LONGBLOB, rules_text LONGTEXT, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
                "CREATE TABLE IF NOT EXISTS fuzzy_stats_v2 (" +
                "attribute_name VARCHAR(50) PRIMARY KEY, " +
                "mean_val DOUBLE, std_dev DOUBLE, weight_factor DOUBLE)",

                // PERBAIKAN DI SINI: Menghapus DEFAULT 1 agar kompatibel dengan H2
                "CREATE TABLE IF NOT EXISTS model_evaluation (" +
                "id INT PRIMARY KEY, " + 
                "tp INT, tn INT, fp INT, fn INT, " +
                "accuracy DOUBLE, precision_val DOUBLE, recall DOUBLE, f1 DOUBLE, " +
                "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP)"
            };

            for (String sql : tables) {
                stmt.execute(sql);
            }

            // Bagian ini sekarang akan tereksekusi karena tabel di atas tidak error lagi
            ResultSet rsUser = stmt.executeQuery("SELECT COUNT(*) FROM users");
            if (rsUser.next() && rsUser.getInt(1) == 0) {
                logger.info("[INIT] Seeding user admin & default...");
                stmt.execute("INSERT INTO users VALUES ('" + UUID.randomUUID() + "', 'admin', 'admin123', 'Dr. Strange', 'EXPERT')");
                stmt.execute("INSERT INTO users VALUES ('" + UUID.randomUUID() + "', 'user', 'user123', 'Tony Stark', 'PATIENT')");
            }

            ResultSet rsData = stmt.executeQuery("SELECT COUNT(*) FROM dataset_cardio");
            if (rsData.next() && rsData.getInt(1) == 0) {
                seedDefaultData(c);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void seedDefaultData(Connection c) {
        logger.info("[INIT] Database kosong. Mencoba load 'default.csv' dari resources...");
        try (InputStream is = DBConnect.class.getResourceAsStream("/default.csv")) {
            if (is == null) {
                logger.warn("[INIT] 'default.csv' tidak ditemukan di resources. Skip seeding.");
                return;
            }

            String sql = "INSERT INTO dataset_cardio VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is));
                 PreparedStatement ps = c.prepareStatement(sql)) {
                
                c.setAutoCommit(false);
                String line;
                br.readLine(); 
                
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] data = line.split(";");
                    if (data.length < 13) continue;

                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setInt(2, Integer.parseInt(data[1]));
                    ps.setInt(3, Integer.parseInt(data[2]));
                    ps.setInt(4, Integer.parseInt(data[3]));
                    ps.setDouble(5, Double.parseDouble(data[4]));
                    ps.setInt(6, Integer.parseInt(data[5]));
                    ps.setInt(7, Integer.parseInt(data[6]));
                    ps.setInt(8, Integer.parseInt(data[7]));
                    ps.setInt(9, Integer.parseInt(data[8]));
                    ps.setInt(10, Integer.parseInt(data[9]));
                    ps.setInt(11, Integer.parseInt(data[10]));
                    ps.setInt(12, Integer.parseInt(data[11]));
                    ps.setInt(13, Integer.parseInt(data[12]));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
                c.setAutoCommit(true);
                logger.info("[INIT] Berhasil import data default.");
                
                TrainingService.trainSystemFromDb();
            }
        } catch (Exception e) {
            logger.error("[INIT] Gagal seeding default data", e);
        }
    }
}