package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

public enum ShaderQuality {
    NATIVE("原生 (4K/关闭)", null),
    Q1080("1080p", ResourceUtil.createInstance("shaders/post/pixelate_1080.json")),
    Q720 ("720p",  ResourceUtil.createInstance("shaders/post/pixelate_720.json")),
    Q480 ("480p",  ResourceUtil.createInstance("shaders/post/pixelate_480.json")),
    Q270 ("270p",  ResourceUtil.createInstance("shaders/post/pixelate_270.json")),
    Q144 ("144p",  ResourceUtil.createInstance("shaders/post/pixelate_144.json")),
    Q72  ("72p",   ResourceUtil.createInstance("shaders/post/pixelate_72.json")),
    Q36  ("36p",   ResourceUtil.createInstance("shaders/post/pixelate_36.json"));

    public final String label;
    public final ResourceLocation effect;

    ShaderQuality(String label, ResourceLocation effect) {
        this.label = label;
        this.effect = effect;
    }

    public ShaderQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }
}