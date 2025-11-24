package com.praktikumpbo.cardioexpert;

import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import java.io.File;

public class AdaptiveExpertSystem {

    private J48 classifier;
    private Instances dataset;

    public static void main(String[] args) {
        AdaptiveExpertSystem system = new AdaptiveExpertSystem();
        
        try {
            System.out.println("=== MEMULAI PROSES INDUCTIVE LEARNING ===\n");

            // 1. Load Datasetadmin
            System.out.println("[1] Loading Data...");
            Instances allData = system.loadData("cardio_train.csv");
            
            // 2. Preprocessing (Convert Target 'cardio' to Nominal)
            System.out.println("[2] Preprocessing Data...");
            allData = system.preprocessData(allData);

            // --- SIMULASI CONCEPT DRIFT ---
            
            // Skenario A: Training dengan SEDIKIT data (Misal 500 data awal)
            System.out.println("\n--- SKENARIO A: TRAINING DATASET KECIL (500 Data) ---");
            Instances dataA = new Instances(allData, 0, 500);
            system.trainModel(dataA);
            System.out.println("\n[RULE BASE VERSI A - DIHASILKAN OTOMATIS]:");
            System.out.println(system.classifier.toString()); // Mencetak Pohon Keputusan

            // Skenario B: Training dengan SEMUA data (70.000 Data)
            // Ini mensimulasikan "Update Pengetahuan" saat data medis baru tersedia
            System.out.println("\n--- SKENARIO B: RETRAINING FULL DATASET (70.000 Data) ---");
            system.trainModel(allData);
            System.out.println("\n[RULE BASE VERSI B - LEBIH AKURAT & KOMPLEKS]:");
            // Mencetak graph (bisa divisualisasikan di Weka GUI) atau tree text
            System.out.println(system.classifier.graph()); 

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 1. DATA LOADER
    public Instances loadData(String filePath) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(filePath));
        loader.setFieldSeparator(";"); // Penting: CSV ini pakai semicolon
        return loader.getDataSet();
    }

    // 2. PREPROCESSING
    public Instances preprocessData(Instances data) throws Exception {
        // Set Class Index (Target Variable) ke kolom terakhir ('cardio')
        data.setClassIndex(data.numAttributes() - 1);

        // Konversi kolom target (cardio) dari Numeric (0,1) ke Nominal {0,1}
        // Agar J48 menganggapnya sebagai Klasifikasi, bukan Angka Regresi
        NumericToNominal convert = new NumericToNominal();
        convert.setAttributeIndices("last"); // Kolom terakhir
        convert.setInputFormat(data);
        return Filter.useFilter(data, convert);
    }

    // 3. MODEL TRAINING (INDUCTIVE LEARNING)
    public void trainModel(Instances trainingData) throws Exception {
        // Menggunakan J48 (Implementasi C4.5 Decision Tree di Java)
        classifier = new J48();
        
        // Opsi J48: -C 0.25 (Confidence threshold untuk pruning) -M 2 (Min instances per leaf)
        classifier.setOptions(new String[]{"-C", "0.25", "-M", "2"});
        classifier.buildClassifier(trainingData);
        
        System.out.println("Training Selesai. Model berhasil dibangun dari " + trainingData.numInstances() + " data pasien.");
    }
}