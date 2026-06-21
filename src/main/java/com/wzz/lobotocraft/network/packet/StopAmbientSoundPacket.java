package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 停止音效数据包（服务器 -> 客户端）
 * 用于停止特定实体的环境音效
 */
public class StopAmbientSoundPacket implements IMessage {
    private ResourceLocation soundLocation;
    private SoundSource soundSource;
    
    public StopAmbientSoundPacket(ResourceLocation soundLocation, SoundSource soundSource) {
        this.soundLocation = soundLocation;
        this.soundSource = soundSource;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.soundLocation = buf.readResourceLocation();
        this.soundSource = buf.readEnum(SoundSource.class);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(soundLocation);
        buf.writeEnum(soundSource);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(soundLocation, soundSource);
        }
    }
}