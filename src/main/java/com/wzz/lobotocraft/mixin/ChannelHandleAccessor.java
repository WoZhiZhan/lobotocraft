package com.wzz.lobotocraft.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ChannelHandle的访问器Mixin
 * 用于暴露Channel对象，进而访问OpenAL的source ID
 */
@Mixin(net.minecraft.client.sounds.ChannelAccess.ChannelHandle.class)
public interface ChannelHandleAccessor {
    
    /**
     * 获取Channel对象
     */
    @Accessor("channel")
    Channel getChannel();
}