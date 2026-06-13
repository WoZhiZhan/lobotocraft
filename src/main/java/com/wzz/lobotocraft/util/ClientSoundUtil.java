package com.wzz.lobotocraft.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public class ClientSoundUtil {
    
    /**
     * 为本地玩家播放音效
     */
    public static void playSoundForLocalPlayer(String soundName, LivingEntity entity) {
        playSoundForLocalPlayer(soundName, entity, SoundSource.RECORDS);
    }
    
    /**
     * 为本地玩家播放音效（指定音源类别）
     */
    public static void playSoundForLocalPlayer(String soundName, LivingEntity entity, SoundSource soundSource) {
        if (entity == null || !entity.level.isClientSide()) {
            return;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity != minecraft.player) {
            return;
        }
        
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(
                ResourceUtil.createInstanceWithColon(soundName)
        );
        
        if (soundEvent != null) {
            minecraft.getSoundManager().play(
                    new SimpleSoundInstance(
                            soundEvent,
                            soundSource,
                            1.0f,
                            1.0f,
                            entity.getRandom(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ()
                    )
            );
        }
    }
}