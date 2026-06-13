package com.wzz.lobotocraft.client.audio;

import com.mojang.blaze3d.audio.Channel;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.mixin.ChannelAccessor;
import com.wzz.lobotocraft.mixin.ChannelHandleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 音频位置设置工具类
 * 使用Mixin和反射来设置音频播放位置
 */
public class AudioPositionUtil {
    
    private static Field instanceToChannelField;
    
    static {
        try {
            // 获取SoundEngine的instanceToChannel字段
            instanceToChannelField = SoundEngine.class.getDeclaredField("instanceToChannel");
            instanceToChannelField.setAccessible(true);
        } catch (Exception e) {
            // 尝试其他可能的字段名（不同版本可能不同）
            try {
                instanceToChannelField = SoundEngine.class.getDeclaredField("f_120226_");
                instanceToChannelField.setAccessible(true);
            } catch (Exception e2) {
                ModLogger.error("[音频工具] 反射失败，无法设置音频位置");
            }
        }
    }

    /**
     * 设置音频播放位置（针对流式音频优化）
     * @param instance 音频实例
     * @param timeSeconds 播放位置（秒）
     * @return 是否成功
     */
    public static boolean setPlaybackPosition(SoundInstance instance, float timeSeconds) {
        if (instanceToChannelField == null) {
            return false;
        }

        try {
            SoundEngine soundEngine = Minecraft.getInstance().getSoundManager().soundEngine;

            // 获取instanceToChannel映射
            @SuppressWarnings("unchecked")
            Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel =
                    (Map<SoundInstance, ChannelAccess.ChannelHandle>) instanceToChannelField.get(soundEngine);

            // 获取对应的ChannelHandle
            ChannelAccess.ChannelHandle channelHandle = instanceToChannel.get(instance);

            if (channelHandle == null) {
                ModLogger.error("[音频工具] 未找到音频通道");
                return false;
            }

            // 使用Mixin访问器获取Channel
            if (channelHandle instanceof ChannelHandleAccessor) {
                Channel channel = ((ChannelHandleAccessor) channelHandle).getChannel();

                if (channel == null) {
                    ModLogger.error("[音频工具] Channel为null");
                    return false;
                }

                // 使用Mixin访问器获取source
                if (channel instanceof ChannelAccessor) {
                    int source = ((ChannelAccessor) channel).getSource();

                    // 针对流式音频的特殊处理
                    // 1. 获取当前播放状态
                    int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);

                    // 2. 如果正在播放，先暂停
                    boolean wasPlaying = (state == AL10.AL_PLAYING);
                    if (wasPlaying) {
                        AL10.alSourcePause(source);
                        // 等待暂停完成
                        Thread.sleep(10);
                    }

                    // 设置播放位置
                    AL10.alSourcef(source, AL11.AL_SEC_OFFSET, timeSeconds);

                    // 检查是否有错误
                    int error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        ModLogger.error("[音频工具] 设置位置时出错，OpenAL错误码: " + error);
                        // 即使出错也继续播放
                        if (wasPlaying) {
                            AL10.alSourcePlay(source);
                        }
                        return false;
                    }

                    // 恢复播放
                    if (wasPlaying) {
                        AL10.alSourcePlay(source);
                    }
                    return true;
                } else {
                    ModLogger.error("[音频工具] Channel不是ChannelAccessor类型");
                    return false;
                }
            } else {
                ModLogger.error("[音频工具] ChannelHandle不是ChannelHandleAccessor类型");
                return false;
            }

        } catch (Exception e) {
            ModLogger.error("[音频工具] 设置播放位置失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前播放位置
     * @param instance 音频实例
     * @return 播放位置（秒），失败返回-1
     */
    public static float getPlaybackPosition(SoundInstance instance) {
        if (instanceToChannelField == null) {
            return -1;
        }

        try {
            SoundEngine soundEngine = Minecraft.getInstance().getSoundManager().soundEngine;

            @SuppressWarnings("unchecked")
            Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel =
                    (Map<SoundInstance, ChannelAccess.ChannelHandle>) instanceToChannelField.get(soundEngine);

            ChannelAccess.ChannelHandle channelHandle = instanceToChannel.get(instance);

            if (channelHandle != null && channelHandle instanceof ChannelHandleAccessor) {
                Channel channel = ((ChannelHandleAccessor) channelHandle).getChannel();

                if (channel != null && channel instanceof ChannelAccessor) {
                    int source = ((ChannelAccessor) channel).getSource();
                    return AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
                }
            }

        } catch (Exception e) {
            System.err.println("[音频工具] 获取播放位置失败: " + e.getMessage());
        }

        return -1;
    }
}