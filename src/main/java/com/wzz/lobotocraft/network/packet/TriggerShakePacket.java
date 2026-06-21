package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.event.ScreenSnakeEvent;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class TriggerShakePacket implements IMessage {
    private int tick;
    private float intensity = 10.0F; // 默认与碧蓝新星一致

    public TriggerShakePacket(int tick) {
        this.tick = tick;
    }

    /** 自定义扭曲强度(例如狼嚎使用碧蓝新星一半的强度) */
    public TriggerShakePacket(int tick, float intensity) {
        this.tick = tick;
        this.intensity = intensity;
    }

    @Override
    public boolean sendToClient() { return true; }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.tick = buf.readInt();
        this.intensity = buf.readFloat();
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(tick);
        buf.writeFloat(intensity);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        ScreenSnakeEvent.triggerShake(tick, intensity);
    }
}