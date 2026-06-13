package com.wzz.lobotocraft.util;

public class MathUtil {
    public static int convertToInteger(Number number) {
        return number.intValue();
    }

    public static float convertToFloat(Number number) {
        return number.floatValue();
    }

    public static double convertToDouble(Number number) {
        return number.doubleValue();
    }

    public static long convertToLong(Number number) {
        return number.longValue();
    }

    public static byte convertToByte(Number number) {
        return number.byteValue();
    }

    public static short convertToShort(Number number) {
        return number.shortValue();
    }
}
