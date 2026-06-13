package com.wzz.lobotocraft.util;

import java.util.Random;

public class RandomGenerator {
    public static String generateRandomString() {
        String letterCharset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String alphanumericCharset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        int length = random.nextInt(21) + 5;
        StringBuilder result = new StringBuilder(length);
        int firstIndex = random.nextInt(letterCharset.length());
        result.append(letterCharset.charAt(firstIndex));
        for (int i = 1; i < length; i++) {
            int index = random.nextInt(alphanumericCharset.length());
            result.append(alphanumericCharset.charAt(index));
        }
        return result.toString();
    }

    public static long generateRandomNumber() {
        String numericCharset = "0123456789";
        Random random = new Random();
        int length = random.nextInt(10) + 5;
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(numericCharset.length());
            result.append(numericCharset.charAt(index));
        }
        try {
            return Long.parseLong(result.toString());
        } catch (NumberFormatException e) {
            return generateRandomNumber();
        }
    }

    public static int generateRandomNumberInt() {
        String numericCharset = "0123456789";
        Random random = new Random();
        int length = random.nextInt(10);
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(numericCharset.length());
            result.append(numericCharset.charAt(index));
        }
        try {
            return Integer.parseInt(result.toString());
        } catch (NumberFormatException e) {
            return generateRandomNumberInt();
        }
    }

    public static String generateRandomStringShort() {
        String letterCharset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String alphanumericCharset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        int length = random.nextInt(11) + 5;
        StringBuilder result = new StringBuilder(length);
        int firstIndex = random.nextInt(letterCharset.length());
        result.append(letterCharset.charAt(firstIndex));
        for (int i = 1; i < length; i++) {
            int index = random.nextInt(alphanumericCharset.length());
            result.append(alphanumericCharset.charAt(index));
        }
        return result.toString();
    }

    public static char getRandomChar() {
        String chars = "!@#$%^&*?";
        return chars.charAt((int) (Math.random() * chars.length()));
    }
}