package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.overlay.OrdealTitleOverlay;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：显示自定义考验标题。
 * 记得在 MessageLoader 里按你现有的方式注册（PLAY_TO_CLIENT）。
 */
public class OrdealTitlePacket implements IMessage {

    private String top;
    private String title;
    private String subtitle;
    private int themeColor;
    private int fadeIn;
    private int stay;
    private int fadeOut;

    public OrdealTitlePacket(String top, String title, String subtitle, int themeColor,
                             int fadeIn, int stay, int fadeOut) {
        this.top = top == null ? "" : top;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.themeColor = themeColor;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.top = buf.readUtf();
        this.title = buf.readUtf();
        this.subtitle = buf.readUtf();
        this.themeColor = buf.readInt();
        this.fadeIn = buf.readInt();
        this.stay = buf.readInt();
        this.fadeOut = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(top);
        buf.writeUtf(title);
        buf.writeUtf(subtitle);
        buf.writeInt(themeColor);
        buf.writeInt(fadeIn);
        buf.writeInt(stay);
        buf.writeInt(fadeOut);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        OrdealTitleOverlay.show(
                                Component.literal(top),
                                Component.literal(title),
                                Component.literal(subtitle),
                                themeColor, fadeIn, stay, fadeOut)));
    }
}