package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.damage_border.DamageBorderEffect;
import com.wzz.lobotocraft.client.damage_border.DamageBorderHud;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 伤害边框效果同步消息
 * 服务端发送给客户端，通知显示对应类型的伤害边框
 */
public class DamageBorderPacket implements IMessage {
    private String damageTypeId;
    
    public DamageBorderPacket(String damageTypeId) {
        this.damageTypeId = damageTypeId;
    }

    @Override
    public boolean sendToClient() { return true; }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.damageTypeId = buf.readUtf();
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(damageTypeId);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        DamageBorderEffect.DamageType damageType = DamageBorderEffect.DamageType.fromString(damageTypeId);
        if (damageType != null) {
            DamageBorderHud.addBorderEffect(damageType);
        }
    }
}