package com.praktikumpbo.cardioexpert;

public class UserSession {
    public static String id;
    public static String role;
    public static String name;

    public static boolean isGuest() {
        return id == null;
    }

    public static void clear() {
        id = null;
        role = null;
        name = null;
    }
}