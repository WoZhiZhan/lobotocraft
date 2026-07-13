package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public class SoundUtil {
    
    /**
     * 播放模组音效（通用）
     */
    public static void playModSound(LevelAccessor world, String soundName, LivingEntity entity) {
        playModSound(world, soundName, entity, SoundSource.RECORDS);
    }
    
    /**
     * 播放模组音效（指定音源类别）
     */
    public static void playModSound(LevelAccessor world, String soundName, 
                                   LivingEntity entity, SoundSource soundSource) {
        if (world instanceof Level level && entity != null) {
            try {
                SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(
                        ResourceUtil.createInstanceWithColon("lobotocraft:" + soundName)
                );
                
                if (soundEvent == null) {
                    ModLogger.error("音效未找到: lobotocraft:" + soundName);
                    return;
                }
                
                if (!level.isClientSide()) {
                    // 服务端播放
                    level.playSound(null, entity.blockPosition(), soundEvent, soundSource, 1.0f, 1.0f);
                } else {
                    // 客户端本地播放
                    level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), 
                            soundEvent, soundSource, 1.0f, 1.0f, false);
                }
            } catch (Exception e) {
                ModLogger.LOGGER.error("播放音效失败: {}", soundName, e);
            }
        }
    }

    /**
     * 播放模组音效（通用）
     */
    public static void playModSound(LevelAccessor world, SoundEvent soundEvent, LivingEntity entity) {
        playModSound(world, soundEvent, entity, SoundSource.RECORDS);
    }

    /**
     * 播放模组音效（指定音源类别）
     */
    public static void playModSound(LevelAccessor world, SoundEvent soundEvent,
                                    LivingEntity entity, SoundSource soundSource) {
        if (world instanceof Level level && entity != null) {
            try {
                if (!level.isClientSide()) {
                    // 服务端播放
                    level.playSound(null, entity.blockPosition(), soundEvent, soundSource, 1.0f, 1.0f);
                } else {
                    // 客户端本地播放
                    level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                            soundEvent, soundSource, 1.0f, 1.0f, false);
                }
            } catch (Exception e) {
                ModLogger.LOGGER.error("播放音效失败: {}", soundEvent, e);
            }
        }
    }
    
    /**
     * 在指定位置播放音效
     */
    public static void playSoundAtPosition(Level world, BlockPos pos, SoundEvent sound, 
                                          SoundSource source, float volume, float pitch) {
        if (world == null || pos == null || sound == null) return;
        
        if (!world.isClientSide()) {
            world.playSound(null, pos, sound, source, volume, pitch);
        } else {
            world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), 
                    sound, source, volume, pitch, false);
        }
    }

    public static void playSound(Level level, LivingEntity entity, SoundEvent sound) {
        playSound(level, entity, sound, 1.0f);
    }

    public static void playSound(Level level, LivingEntity entity, SoundEvent sound, float volume) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    sound, entity.getSoundSource(), volume, 1.0f);
        }
    }
}