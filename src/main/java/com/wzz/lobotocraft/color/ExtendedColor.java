package com.wzz.lobotocraft.color;

import net.minecraft.network.chat.Style;

import java.awt.*;

/**
 * 扩展的Color类，提供更多预定义颜色常量
 * 包含Web标准颜色、Material Design颜色、游戏主题颜色等
 */
public class ExtendedColor extends Color {

    public static final ExtendedColor BLACK = new ExtendedColor(0,0,0);
    public static final ExtendedColor WHITE = new ExtendedColor(255,255,255);
    public static final ExtendedColor RED = new ExtendedColor(255,0,0);
    public static final ExtendedColor GREEN = new ExtendedColor(0,255,0);
    public static final ExtendedColor BLUE = new ExtendedColor(0,0,255);
    public static final ExtendedColor YELLOW = new ExtendedColor(255,255,0);
    public static final ExtendedColor CYAN = new ExtendedColor(0,255,255);
    public static final ExtendedColor MAGENTA = new ExtendedColor(255,0,255);
    public static final ExtendedColor ORANGE = new ExtendedColor(255,200,0);
    public static final ExtendedColor PINK = new ExtendedColor(255,175,175);
    public static final ExtendedColor GRAY = new ExtendedColor(128,128,128);
    public static final ExtendedColor DARK_GRAY = new ExtendedColor(64,64,64);
    public static final ExtendedColor LIGHT_GRAY = new ExtendedColor(192,192,192);

    // ==================== 红色系 ====================
    public static final ExtendedColor CRIMSON = new ExtendedColor(220, 20, 60);
    public static final ExtendedColor DARK_RED = new ExtendedColor(139, 0, 0);
    public static final ExtendedColor INDIAN_RED = new ExtendedColor(205, 92, 92);
    public static final ExtendedColor LIGHT_CORAL = new ExtendedColor(240, 128, 128);
    public static final ExtendedColor SALMON = new ExtendedColor(250, 128, 114);
    public static final ExtendedColor DARK_SALMON = new ExtendedColor(233, 150, 122);
    public static final ExtendedColor LIGHT_SALMON = new ExtendedColor(255, 160, 122);
    public static final ExtendedColor TOMATO = new ExtendedColor(255, 99, 71);
    public static final ExtendedColor ORANGE_RED = new ExtendedColor(255, 69, 0);
    public static final ExtendedColor FIREBRICK = new ExtendedColor(178, 34, 34);
    public static final ExtendedColor SCARLET = new ExtendedColor(255, 36, 0);
    public static final ExtendedColor RUBY = new ExtendedColor(224, 17, 95);
    public static final ExtendedColor BLOOD_RED = new ExtendedColor(136, 8, 8);
    public static final ExtendedColor DEEP_RED = new ExtendedColor(185, 0, 0);

    // ==================== 橙色系 ====================
    public static final ExtendedColor DARK_ORANGE = new ExtendedColor(255, 140, 0);
    public static final ExtendedColor CORAL = new ExtendedColor(255, 127, 80);
    public static final ExtendedColor PEACH_PUFF = new ExtendedColor(255, 218, 185);
    public static final ExtendedColor PAPAYA_WHIP = new ExtendedColor(255, 239, 213);
    public static final ExtendedColor MOCCASIN = new ExtendedColor(255, 228, 181);
    public static final ExtendedColor TANGERINE = new ExtendedColor(255, 130, 67);
    public static final ExtendedColor BURNT_ORANGE = new ExtendedColor(204, 85, 0);
    public static final ExtendedColor SUNSET_ORANGE = new ExtendedColor(255, 100, 50);

    // ==================== 黄色系 ====================
    public static final ExtendedColor GOLD = new ExtendedColor(255, 215, 0);
    public static final ExtendedColor KHAKI = new ExtendedColor(240, 230, 140);
    public static final ExtendedColor DARK_KHAKI = new ExtendedColor(189, 183, 107);
    public static final ExtendedColor LEMON_CHIFFON = new ExtendedColor(255, 250, 205);
    public static final ExtendedColor LIGHT_YELLOW = new ExtendedColor(255, 255, 224);
    public static final ExtendedColor LIGHT_GOLDENROD_YELLOW = new ExtendedColor(250, 250, 210);
    public static final ExtendedColor GOLDEN_YELLOW = new ExtendedColor(255, 223, 0);
    public static final ExtendedColor CANARY_YELLOW = new ExtendedColor(255, 239, 0);
    public static final ExtendedColor MUSTARD = new ExtendedColor(255, 219, 88);
    public static final ExtendedColor GOLDENROD = new ExtendedColor(218, 165, 32);
    public static final ExtendedColor LEMON_YELLOW = new ExtendedColor(255, 247, 0);

    // ==================== 绿色系 ====================
    public static final ExtendedColor LIME = new ExtendedColor(0, 255, 0);
    public static final ExtendedColor LIME_GREEN = new ExtendedColor(50, 205, 50);
    public static final ExtendedColor DARK_GREEN = new ExtendedColor(0, 100, 0);
    public static final ExtendedColor FOREST_GREEN = new ExtendedColor(34, 139, 34);
    public static final ExtendedColor SEA_GREEN = new ExtendedColor(46, 139, 87);
    public static final ExtendedColor MEDIUM_SEA_GREEN = new ExtendedColor(60, 179, 113);
    public static final ExtendedColor SPRING_GREEN = new ExtendedColor(0, 255, 127);
    public static final ExtendedColor MINT_CREAM = new ExtendedColor(245, 255, 250);
    public static final ExtendedColor LIGHT_GREEN = new ExtendedColor(144, 238, 144);
    public static final ExtendedColor PALE_GREEN = new ExtendedColor(152, 251, 152);
    public static final ExtendedColor DARK_OLIVE_GREEN = new ExtendedColor(85, 107, 47);
    public static final ExtendedColor OLIVE = new ExtendedColor(128, 128, 0);
    public static final ExtendedColor OLIVE_DRAB = new ExtendedColor(107, 142, 35);
    public static final ExtendedColor YELLOW_GREEN = new ExtendedColor(154, 205, 50);
    public static final ExtendedColor CHARTREUSE = new ExtendedColor(127, 255, 0);
    public static final ExtendedColor LAWN_GREEN = new ExtendedColor(124, 252, 0);
    public static final ExtendedColor EMERALD = new ExtendedColor(80, 200, 120);
    public static final ExtendedColor JADE = new ExtendedColor(0, 168, 107);
    public static final ExtendedColor MINT = new ExtendedColor(62, 180, 137);
    public static final ExtendedColor NEON_GREEN = new ExtendedColor(57, 255, 20);
    public static final ExtendedColor MINT_GREEN = new ExtendedColor(152, 255, 152);

    // ==================== 青色系 ====================
    public static final ExtendedColor AQUA = new ExtendedColor(0, 255, 255);
    public static final ExtendedColor AQUAMARINE = new ExtendedColor(127, 255, 212);
    public static final ExtendedColor TURQUOISE = new ExtendedColor(64, 224, 208);
    public static final ExtendedColor MEDIUM_TURQUOISE = new ExtendedColor(72, 209, 204);
    public static final ExtendedColor DARK_TURQUOISE = new ExtendedColor(0, 206, 209);
    public static final ExtendedColor LIGHT_CYAN = new ExtendedColor(224, 255, 255);
    public static final ExtendedColor DARK_CYAN = new ExtendedColor(0, 139, 139);
    public static final ExtendedColor TEAL = new ExtendedColor(0, 128, 128);
    public static final ExtendedColor ELECTRIC_BLUE = new ExtendedColor(125, 249, 255);
    public static final ExtendedColor DEEP_TURQUOISE = new ExtendedColor(0, 150, 150);

    // ==================== 蓝色系 ====================
    public static final ExtendedColor NAVY = new ExtendedColor(0, 0, 128);
    public static final ExtendedColor DARK_BLUE = new ExtendedColor(0, 0, 139);
    public static final ExtendedColor MEDIUM_BLUE = new ExtendedColor(0, 0, 205);
    public static final ExtendedColor ROYAL_BLUE = new ExtendedColor(65, 105, 225);
    public static final ExtendedColor STEEL_BLUE = new ExtendedColor(70, 130, 180);
    public static final ExtendedColor DODGER_BLUE = new ExtendedColor(30, 144, 255);
    public static final ExtendedColor DEEP_SKY_BLUE = new ExtendedColor(0, 191, 255);
    public static final ExtendedColor SKY_BLUE = new ExtendedColor(135, 206, 235);
    public static final ExtendedColor LIGHT_SKY_BLUE = new ExtendedColor(135, 206, 250);
    public static final ExtendedColor LIGHT_BLUE = new ExtendedColor(173, 216, 230);
    public static final ExtendedColor POWDER_BLUE = new ExtendedColor(176, 224, 230);
    public static final ExtendedColor CORNFLOWER_BLUE = new ExtendedColor(100, 149, 237);
    public static final ExtendedColor CADET_BLUE = new ExtendedColor(95, 158, 160);
    public static final ExtendedColor MIDNIGHT_BLUE = new ExtendedColor(25, 25, 112);
    public static final ExtendedColor SAPPHIRE = new ExtendedColor(15, 82, 186);
    public static final ExtendedColor COBALT = new ExtendedColor(0, 71, 171);
    public static final ExtendedColor NEON_BLUE = new ExtendedColor(77, 77, 255);
    public static final ExtendedColor ARCTIC_BLUE = new ExtendedColor(130, 200, 255);

    // ==================== 紫色系 ====================
    public static final ExtendedColor PURPLE = new ExtendedColor(128, 0, 128);
    public static final ExtendedColor INDIGO = new ExtendedColor(75, 0, 130);
    public static final ExtendedColor DARK_VIOLET = new ExtendedColor(148, 0, 211);
    public static final ExtendedColor DARK_ORCHID = new ExtendedColor(153, 50, 204);
    public static final ExtendedColor MEDIUM_ORCHID = new ExtendedColor(186, 85, 211);
    public static final ExtendedColor ORCHID = new ExtendedColor(218, 112, 214);
    public static final ExtendedColor VIOLET = new ExtendedColor(238, 130, 238);
    public static final ExtendedColor PLUM = new ExtendedColor(221, 160, 221);
    public static final ExtendedColor THISTLE = new ExtendedColor(216, 191, 216);
    public static final ExtendedColor LAVENDER = new ExtendedColor(230, 230, 250);
    public static final ExtendedColor MEDIUM_PURPLE = new ExtendedColor(147, 112, 219);
    public static final ExtendedColor BLUE_VIOLET = new ExtendedColor(138, 43, 226);
    public static final ExtendedColor DARK_MAGENTA = new ExtendedColor(139, 0, 139);
    public static final ExtendedColor MEDIUM_VIOLET_RED = new ExtendedColor(199, 21, 133);
    public static final ExtendedColor AMETHYST = new ExtendedColor(153, 102, 204);
    public static final ExtendedColor NEON_PURPLE = new ExtendedColor(191, 64, 191);
    public static final ExtendedColor SOFT_PURPLE = new ExtendedColor(180, 150, 200);
    public static final ExtendedColor LIGHT_PURPLE = new ExtendedColor(200, 162, 255);

    // ==================== 粉色系 ====================
    public static final ExtendedColor LIGHT_PINK = new ExtendedColor(255, 182, 193);
    public static final ExtendedColor HOT_PINK = new ExtendedColor(255, 105, 180);
    public static final ExtendedColor DEEP_PINK = new ExtendedColor(255, 20, 147);
    public static final ExtendedColor PALE_VIOLET_RED = new ExtendedColor(219, 112, 147);
    public static final ExtendedColor FUCHSIA = new ExtendedColor(255, 0, 255);
    public static final ExtendedColor ROSE = new ExtendedColor(255, 0, 127);
    public static final ExtendedColor ROSE_PINK = new ExtendedColor(255, 102, 153);

    // ==================== 棕色系 ====================
    public static final ExtendedColor BROWN = new ExtendedColor(165, 42, 42);
    public static final ExtendedColor MAROON = new ExtendedColor(128, 0, 0);
    public static final ExtendedColor SIENNA = new ExtendedColor(160, 82, 45);
    public static final ExtendedColor SADDLE_BROWN = new ExtendedColor(139, 69, 19);
    public static final ExtendedColor CHOCOLATE = new ExtendedColor(210, 105, 30);
    public static final ExtendedColor PERU = new ExtendedColor(205, 133, 63);
    public static final ExtendedColor SANDY_BROWN = new ExtendedColor(244, 164, 96);
    public static final ExtendedColor BURLYWOOD = new ExtendedColor(222, 184, 135);
    public static final ExtendedColor TAN = new ExtendedColor(210, 180, 140);
    public static final ExtendedColor ROSY_BROWN = new ExtendedColor(188, 143, 143);
    public static final ExtendedColor WHEAT = new ExtendedColor(245, 222, 179);
    public static final ExtendedColor BRONZE = new ExtendedColor(205, 127, 50);
    public static final ExtendedColor COPPER = new ExtendedColor(184, 115, 51);

    // ==================== 灰白色系 ====================
    public static final ExtendedColor SNOW = new ExtendedColor(255, 250, 250);
    public static final ExtendedColor HONEYDEW = new ExtendedColor(240, 255, 240);
    public static final ExtendedColor AZURE = new ExtendedColor(240, 255, 255);
    public static final ExtendedColor ALICE_BLUE = new ExtendedColor(240, 248, 255);
    public static final ExtendedColor GHOST_WHITE = new ExtendedColor(248, 248, 255);
    public static final ExtendedColor WHITE_SMOKE = new ExtendedColor(245, 245, 245);
    public static final ExtendedColor SEASHELL = new ExtendedColor(255, 245, 238);
    public static final ExtendedColor BEIGE = new ExtendedColor(245, 245, 220);
    public static final ExtendedColor OLD_LACE = new ExtendedColor(253, 245, 230);
    public static final ExtendedColor FLORAL_WHITE = new ExtendedColor(255, 250, 240);
    public static final ExtendedColor IVORY = new ExtendedColor(255, 255, 240);
    public static final ExtendedColor ANTIQUE_WHITE = new ExtendedColor(250, 235, 215);
    public static final ExtendedColor LINEN = new ExtendedColor(250, 240, 230);
    public static final ExtendedColor LAVENDER_BLUSH = new ExtendedColor(255, 240, 245);
    public static final ExtendedColor MISTY_ROSE = new ExtendedColor(255, 228, 225);
    public static final ExtendedColor GAINSBORO = new ExtendedColor(220, 220, 220);
    public static final ExtendedColor SILVER = new ExtendedColor(192, 192, 192);
    public static final ExtendedColor DIM_GRAY = new ExtendedColor(105, 105, 105);
    public static final ExtendedColor SLATE_GRAY = new ExtendedColor(112, 128, 144);
    public static final ExtendedColor LIGHT_SLATE_GRAY = new ExtendedColor(119, 136, 153);
    public static final ExtendedColor DARK_SLATE_GRAY = new ExtendedColor(47, 79, 79);
    public static final ExtendedColor CHARCOAL = new ExtendedColor(54, 69, 79);

    // ==================== Material Design 主要颜色 ====================
    public static final ExtendedColor MATERIAL_RED = new ExtendedColor(244, 67, 54);
    public static final ExtendedColor MATERIAL_PINK = new ExtendedColor(233, 30, 99);
    public static final ExtendedColor MATERIAL_PURPLE = new ExtendedColor(156, 39, 176);
    public static final ExtendedColor MATERIAL_DEEP_PURPLE = new ExtendedColor(103, 58, 183);
    public static final ExtendedColor MATERIAL_INDIGO = new ExtendedColor(63, 81, 181);
    public static final ExtendedColor MATERIAL_BLUE = new ExtendedColor(33, 150, 243);
    public static final ExtendedColor MATERIAL_LIGHT_BLUE = new ExtendedColor(3, 169, 244);
    public static final ExtendedColor MATERIAL_CYAN = new ExtendedColor(0, 188, 212);
    public static final ExtendedColor MATERIAL_TEAL = new ExtendedColor(0, 150, 136);
    public static final ExtendedColor MATERIAL_GREEN = new ExtendedColor(76, 175, 80);
    public static final ExtendedColor MATERIAL_LIGHT_GREEN = new ExtendedColor(139, 195, 74);
    public static final ExtendedColor MATERIAL_LIME = new ExtendedColor(205, 220, 57);
    public static final ExtendedColor MATERIAL_YELLOW = new ExtendedColor(255, 235, 59);
    public static final ExtendedColor MATERIAL_AMBER = new ExtendedColor(255, 193, 7);
    public static final ExtendedColor MATERIAL_ORANGE = new ExtendedColor(255, 152, 0);
    public static final ExtendedColor MATERIAL_DEEP_ORANGE = new ExtendedColor(255, 87, 34);
    public static final ExtendedColor MATERIAL_BROWN = new ExtendedColor(121, 85, 72);
    public static final ExtendedColor MATERIAL_GREY = new ExtendedColor(158, 158, 158);
    public static final ExtendedColor MATERIAL_BLUE_GREY = new ExtendedColor(96, 125, 139);

    // ==================== 游戏/魔法主题颜色 ====================
    // 元素系
    public static final ExtendedColor FIRE = new ExtendedColor(255, 69, 0);
    public static final ExtendedColor WATER = new ExtendedColor(0, 105, 148);
    public static final ExtendedColor ICE = new ExtendedColor(175, 238, 238);
    public static final ExtendedColor LIGHTNING = new ExtendedColor(255, 255, 102);
    public static final ExtendedColor EARTH = new ExtendedColor(139, 90, 43);
    public static final ExtendedColor WIND = new ExtendedColor(230, 255, 250);
    public static final ExtendedColor NATURE = new ExtendedColor(34, 139, 34);
    public static final ExtendedColor POISON = new ExtendedColor(120, 40, 200);
    public static final ExtendedColor SHADOW = new ExtendedColor(40, 40, 60);
    public static final ExtendedColor LIGHT = new ExtendedColor(255, 250, 205);
    public static final ExtendedColor DARK = new ExtendedColor(25, 25, 35);
    public static final ExtendedColor HOLY = new ExtendedColor(255, 245, 157);
    public static final ExtendedColor ARCANE = new ExtendedColor(148, 0, 211);
    public static final ExtendedColor VOID = new ExtendedColor(72, 0, 128);
    public static final ExtendedColor CHAOS = new ExtendedColor(220, 20, 60);

    // 稀有度颜色（Minecraft/RPG风格）
    public static final ExtendedColor COMMON = new ExtendedColor(170, 170, 170);        // 白色/灰色
    public static final ExtendedColor UNCOMMON = new ExtendedColor(85, 255, 85);        // 绿色
    public static final ExtendedColor RARE = new ExtendedColor(85, 85, 255);            // 蓝色
    public static final ExtendedColor EPIC = new ExtendedColor(170, 0, 170);            // 紫色
    public static final ExtendedColor LEGENDARY = new ExtendedColor(255, 170, 0);       // 金色
    public static final ExtendedColor MYTHIC = new ExtendedColor(255, 85, 255);         // 粉红色
    public static final ExtendedColor DIVINE = new ExtendedColor(85, 255, 255);         // 青色
    public static final ExtendedColor ARTIFACT = new ExtendedColor(255, 128, 0);        // 橙色

    // Minecraft原版颜色
    public static final ExtendedColor MC_OBSIDIAN = new ExtendedColor(16, 12, 26);
    public static final ExtendedColor MC_NETHERITE = new ExtendedColor(68, 58, 59);
    public static final ExtendedColor MC_DIAMOND = new ExtendedColor(93, 236, 229);
    public static final ExtendedColor MC_EMERALD = new ExtendedColor(17, 221, 68);
    public static final ExtendedColor MC_LAPIS = new ExtendedColor(31, 67, 162);
    public static final ExtendedColor MC_REDSTONE = new ExtendedColor(187, 0, 0);
    public static final ExtendedColor MC_GOLD = new ExtendedColor(252, 238, 75);
    public static final ExtendedColor MC_IRON = new ExtendedColor(216, 216, 216);
    public static final ExtendedColor MC_COPPER = new ExtendedColor(181, 103, 77);
    public static final ExtendedColor MC_QUARTZ = new ExtendedColor(233, 229, 220);
    public static final ExtendedColor MC_PRISMARINE = new ExtendedColor(99, 171, 158);
    public static final ExtendedColor MC_GLOWSTONE = new ExtendedColor(255, 198, 99);
    public static final ExtendedColor MC_AMETHYST = new ExtendedColor(145, 88, 202);

    // 发光/霓虹色
    public static final ExtendedColor NEON_RED = new ExtendedColor(255, 16, 16);
    public static final ExtendedColor NEON_ORANGE = new ExtendedColor(255, 128, 0);
    public static final ExtendedColor NEON_YELLOW = new ExtendedColor(255, 255, 16);
    public static final ExtendedColor NEON_PINK = new ExtendedColor(255, 16, 240);
    public static final ExtendedColor NEON_CYAN = new ExtendedColor(0, 255, 255);
    public static final ExtendedColor LASER_RED = new ExtendedColor(255, 0, 0);
    public static final ExtendedColor LASER_GREEN = new ExtendedColor(0, 255, 0);
    public static final ExtendedColor LASER_BLUE = new ExtendedColor(0, 0, 255);

    // 宝石色
    public static final ExtendedColor DIAMOND = new ExtendedColor(185, 242, 255);
    public static final ExtendedColor EMERALD_GEM = new ExtendedColor(80, 200, 120);
    public static final ExtendedColor RUBY_GEM = new ExtendedColor(224, 17, 95);
    public static final ExtendedColor SAPPHIRE_GEM = new ExtendedColor(15, 82, 186);
    public static final ExtendedColor TOPAZ = new ExtendedColor(255, 200, 124);
    public static final ExtendedColor OPAL = new ExtendedColor(168, 195, 188);
    public static final ExtendedColor PEARL = new ExtendedColor(240, 234, 214);
    public static final ExtendedColor ONYX = new ExtendedColor(41, 41, 41);

    // 金属色
    public static final ExtendedColor PLATINUM = new ExtendedColor(229, 228, 226);
    public static final ExtendedColor STEEL = new ExtendedColor(169, 169, 169);
    public static final ExtendedColor TITANIUM = new ExtendedColor(135, 134, 129);
    public static final ExtendedColor MITHRIL = new ExtendedColor(200, 210, 220);
    public static final ExtendedColor ADAMANTITE = new ExtendedColor(180, 50, 50);

    public ExtendedColor(int r, int g, int b) {
        super(r, g, b);
    }

    public ExtendedColor(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    public ExtendedColor(Color color) {
        super(color.getRed(), color.getGreen(), color.getBlue());
    }

    public Style toStyle() {
        return Style.EMPTY.withColor(this.getRGB());
    }

    public int[] toArray() {
        return new int[]{this.getRed(), this.getGreen(), this.getBlue()};
    }

    public int[] toArrayWithAlpha() {
        return new int[]{this.getRed(), this.getGreen(), this.getBlue(), this.getAlpha()};
    }

    public String toHex() {
        return String.format("#%02X%02X%02X", this.getRed(), this.getGreen(), this.getBlue());
    }

    public ExtendedColor lighter(float factor) {
        int r = Math.min(255, (int)(this.getRed() * (1 + factor)));
        int g = Math.min(255, (int)(this.getGreen() * (1 + factor)));
        int b = Math.min(255, (int)(this.getBlue() * (1 + factor)));
        return new ExtendedColor(r, g, b);
    }

    public ExtendedColor darker(float factor) {
        int r = Math.max(0, (int)(this.getRed() * (1 - factor)));
        int g = Math.max(0, (int)(this.getGreen() * (1 - factor)));
        int b = Math.max(0, (int)(this.getBlue() * (1 - factor)));
        return new ExtendedColor(r, g, b);
    }

    public ExtendedColor mix(Color otherColor, float ratio) {
        int r = (int)(this.getRed() * (1 - ratio) + otherColor.getRed() * ratio);
        int g = (int)(this.getGreen() * (1 - ratio) + otherColor.getGreen() * ratio);
        int b = (int)(this.getBlue() * (1 - ratio) + otherColor.getBlue() * ratio);
        return new ExtendedColor(r, g, b);
    }

    public ExtendedColor invert() {
        return new ExtendedColor(
                255 - this.getRed(),
                255 - this.getGreen(),
                255 - this.getBlue()
        );
    }

    public ExtendedColor toGrayscale() {
        int gray = (int)(this.getRed() * 0.299 + this.getGreen() * 0.587 + this.getBlue() * 0.114);
        return new ExtendedColor(gray, gray, gray);
    }

    public ExtendedColor saturate(float amount) {
        float[] hsb = Color.RGBtoHSB(this.getRed(), this.getGreen(), this.getBlue(), null);
        hsb[1] = Math.min(1.0f, hsb[1] + amount);
        return new ExtendedColor(Color.getHSBColor(hsb[0], hsb[1], hsb[2]));
    }

    public ExtendedColor desaturate(float amount) {
        float[] hsb = Color.RGBtoHSB(this.getRed(), this.getGreen(), this.getBlue(), null);
        hsb[1] = Math.max(0.0f, hsb[1] - amount);
        return new ExtendedColor(Color.getHSBColor(hsb[0], hsb[1], hsb[2]));
    }

    public float[] toFloatArray() {
        return new float[]{
                this.getRed() / 255.0f,
                this.getGreen() / 255.0f,
                this.getBlue() / 255.0f
        };
    }

    /**
     * 降低颜色饱和度
     * @param color 原始颜色
     * @param amount 降低量 (0.0-1.0)
     * @return 低饱和度颜色
     */
    public static ExtendedColor desaturate(Color color, float amount) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.desaturate(amount);
    }

    /**
     * 创建彩虹色
     * @param hue 色相 [0, 1]
     * @return 彩虹色
     */
    public static Color rainbow(float hue) {
        return Color.getHSBColor(hue % 1.0f, 1.0f, 1.0f);
    }

    /**
     * 创建彩虹色（带自定义饱和度和亮度）
     * @param hue 色相 [0, 1]
     * @param saturation 饱和度 [0, 1]
     * @param brightness 亮度 [0, 1]
     * @return 彩虹色
     */
    public static Color rainbow(float hue, float saturation, float brightness) {
        return Color.getHSBColor(hue % 1.0f, saturation, brightness);
    }

    /**
     * 根据时间创建动态彩虹色
     * @param time 时间（通常是System.currentTimeMillis() / 1000）
     * @param speed 变化速度
     * @return 动态彩虹色
     */
    public static Color animatedRainbow(float time, float speed) {
        return rainbow((time * speed) % 1.0f);
    }

    public static Style toStyle(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toStyle();
    }

    /**
     * 将Color对象转换为int数组 [r, g, b]
     * @param color Color对象
     * @return RGB数组，值范围 0-255
     */
    public static int[] toArray(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toArray();
    }

    /**
     * 将Color对象转换为带透明度的int数组 [r, g, b, a]
     * @param color Color对象
     * @return RGBA数组，值范围 0-255
     */
    public static int[] toArrayWithAlpha(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toArrayWithAlpha();
    }

    /**
     * 从int数组创建Color对象
     * @param rgb RGB数组 [r, g, b]
     * @return Color对象
     */
    public static ExtendedColor fromArray(int[] rgb) {
        if (rgb.length == 3) {
            return new ExtendedColor(rgb[0], rgb[1], rgb[2]);
        } else if (rgb.length == 4) {
            return new ExtendedColor(rgb[0], rgb[1], rgb[2], rgb[3]);
        }
        throw new IllegalArgumentException("Array must have 3 or 4 elements");
    }

    /**
     * 根据十六进制颜色代码创建Color对象
     * @param hex 十六进制颜色代码，例如 "#FF5733" 或 "FF5733"
     * @return Color对象
     */
    public static Color fromHex(String hex) {
        hex = hex.replace("#", "");
        return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        );
    }

    /**
     * 将Color对象转换为十六进制字符串
     * @param color Color对象
     * @return 十六进制颜色代码，例如 "#FF5733"
     */
    public static String toHex(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toHex();
    }

    /**
     * 使颜色变亮
     * @param color 原始颜色
     * @param factor 变亮系数 (0.0-1.0)
     * @return 变亮后的颜色
     */
    public static Color lighter(Color color, float factor) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.lighter(factor);
    }

    /**
     * 使颜色变暗
     * @param color 原始颜色
     * @param factor 变暗系数 (0.0-1.0)
     * @return 变暗后的颜色
     */
    public static Color darker(Color color, float factor) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.darker(factor);
    }

    /**
     * 混合两种颜色
     * @param color1 颜色1
     * @param color2 颜色2
     * @param ratio 混合比例 [0, 1]，0为完全是color1，1为完全是color2
     * @return 混合后的颜色
     */
    public static ExtendedColor mix(Color color1, Color color2, float ratio) {
        ExtendedColor extendedColor = new ExtendedColor(color1);
        return extendedColor.mix(color2, ratio);
    }

    /**
     * 反转颜色
     * @param color 原始颜色
     * @return 反转后的颜色
     */
    public static ExtendedColor invert(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.invert();
    }

    /**
     * 将颜色转换为灰度
     * @param color 原始颜色
     * @return 灰度颜色
     */
    public static ExtendedColor toGrayscale(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toGrayscale();
    }

    /**
     * 增加颜色饱和度
     * @param color 原始颜色
     * @param amount 增加量 (0.0-1.0)
     * @return 高饱和度颜色
     */
    public static ExtendedColor saturate(Color color, float amount) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.saturate(amount);
    }

    public static float[] toFloatArray(Color color) {
        ExtendedColor extendedColor = new ExtendedColor(color);
        return extendedColor.toFloatArray();
    }
}