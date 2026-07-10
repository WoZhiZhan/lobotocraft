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

    /**
     * 把"想要 tooltip 显示的攻击伤害"换算成需要填的伤害修正值。
     * getAttackDamageBonus() 返回 0,所以只需减去玩家基础伤害 1.0。
     */
    public static int toDamageModifier(int desiredDamage) {
        return desiredDamage - 1; // 玩家基础攻击伤害是 1.0
    }

    /**
     * 把"想要的真实攻击速度"换算成 SwordItem 需要的修正值。
     * 填 1.0 → 实际攻速 1.0,填 2.0 → 实际攻速 2.0,所见即所得。
     */
    public static float toSpeedModifier(float desiredAttackSpeed) {
        return desiredAttackSpeed - 4.0f; // 玩家基础攻速是 4.0
    }
}
