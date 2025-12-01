package com.praktikumpbo.cardioexpert;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvLoader {
    private static final Logger logger = LoggerFactory.getLogger(CsvLoader.class);

    public static void loadCsvToDb(String filePath) {
        logger.info("[CSV] Memulai proses import dari file: {}", filePath);
        String sql = "INSERT INTO dataset_cardio VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        
        try (Connection conn = DBConnect.getConnection();
             BufferedReader br = new BufferedReader(new FileReader(filePath));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            String line; 
            br.readLine(); // Skip header
            
            int count = 0;
            int skipped = 0;
            
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] data = line.split(";");
                if (data.length < 13) {
                    logger.warn("[CSV] Baris diabaikan (kolom kurang): {}", line);
                    skipped++;
                    continue; 
                }

                try {
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
                    
                    count++;
                    if (count % 1000 == 0) {
                        ps.executeBatch();
                        logger.debug("[CSV] Batch insert {} data diproses...", count);
                    }
                } catch (NumberFormatException nfe) {
                    logger.error("[CSV] Gagal parsing angka pada baris: {}", line);
                    skipped++;
                }
            }
            ps.executeBatch(); 
            conn.commit(); 
            conn.setAutoCommit(true);
            
            logger.info("[CSV] Selesai! Sukses: {}, Gagal/Skip: {}", count, skipped);
            
        } catch (Exception e) { 
            logger.error("[CSV] Critical Error saat import CSV", e);
        }
    }
}