package com.wzz.lobotocraft.client.ui;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Player-facing damage names and original inventory icons. "blue" is retained
 * as a legacy combat/save identifier, but is always presented as PALE.
 */
public enum DamageTypeVisual {
    RED("damage_red.png", 122, 120, 0xFFFF0000),
    WHITE("damage_white.png", 116, 122, 0xFFFFFFFF),
    BLACK("damage_black.png", 124, 123, 0xFFAA00AA),
    PALE("damage_pale.png", 116, 120, 0xFF00FFFF),
    ALL(null, 0, 0, 0xFFFFFFFF),
    UNKNOWN(null, 0, 0, 0xFFCCCCCC);

    private final String textureFile;
    private final int textureWidth;
    private final int textureHeight;
    private final int color;
    private ResourceLocation cachedIcon;

    DamageTypeVisual(String textureFile, int textureWidth, int textureHeight, int color) {
        this.textureFile = textureFile;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.color = color;
    }

    public static DamageTypeVisual from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "RED" -> RED;
            case "WHITE" -> WHITE;
            case "BLACK" -> BLACK;
            case "PALE", "BLUE" -> PALE;
            case "ALL" -> ALL;
            default -> UNKNOWN;
        };
    }

    public static String label(String value) {
        DamageTypeVisual visual = from(value);
        return visual == UNKNOWN ? value == null ? "" : value : visual.name();
    }

    public String texturePath() {
        return textureFile == null ? null : "textures/gui/inventory/" + textureFile;
    }

    public ResourceLocation icon() {
        if (textureFile == null) return null;
        if (cachedIcon == null) cachedIcon = ResourceUtil.createInstance(texturePath());
        return cachedIcon;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public int color() {
        return color;
    }
}
