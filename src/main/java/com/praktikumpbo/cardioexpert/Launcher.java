package com.praktikumpbo.cardioexpert;

public class Launcher {
    public static void main(String[] args) {
        DBConnect.initializeDatabase();
        App.main(args);
    }
}