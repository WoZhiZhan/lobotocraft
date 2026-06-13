package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 员工属性同步包 - 服务端发送给客户端
 */
public class EmployeeStatsSyncPacket implements IMessage {
    
    private int fortitude;
    private int prudence;
    private int temperance;
    private int justice;
    
    public EmployeeStatsSyncPacket() {
        // 无参构造器，用于反序列化
    }
    
    public EmployeeStatsSyncPacket(int fortitude, int prudence, int temperance, int justice) {
        this.fortitude = fortitude;
        this.prudence = prudence;
        this.temperance = temperance;
        this.justice = justice;
    }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.fortitude = buf.readInt();
        this.prudence = buf.readInt();
        this.temperance = buf.readInt();
        this.justice = buf.readInt();
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(fortitude);
        buf.writeInt(prudence);
        buf.writeInt(temperance);
        buf.writeInt(justice);
    }
    
    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                        stats.setFortitude(fortitude);
                        stats.setPrudence(prudence);
                        stats.setTemperance(temperance);
                        stats.setJustice(justice);
                    });
                }
            });
        });
    }
    
    @Override
    public boolean sendToClient() {
        return true;
    }
    
    @Override
    public boolean sendToServer() {
        return false;
    }
}
