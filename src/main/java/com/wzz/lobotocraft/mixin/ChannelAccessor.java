package com.wzz.lobotocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Channel的访问器Mixin
 * 用于暴露OpenAL的source ID
 */
@Mixin(com.mojang.blaze3d.audio.Channel.class)
public interface ChannelAccessor {
    
    /**
     * 获取OpenAL的source ID
     * Channel类中通常有一个int类型的source字段
     */
    @Accessor("source")
    int getSource();
}