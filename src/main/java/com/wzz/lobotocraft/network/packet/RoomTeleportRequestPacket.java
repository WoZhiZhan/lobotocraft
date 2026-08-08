package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端：请求传送到某个收容间。
 * 服务端会重新校验一遍（玩家身上有没有终端、目标是不是一个登记过的信标），不信任客户端发来的坐标。
 */
public class RoomTeleportRequestPacket implements IMessage {

    private static final int COOLDOWN_TICKS = 60;

    /**
     * 末影人瞬移音效。
     * 用注册表查而不是直接写 SoundEvents.ENDERMAN_TELEPORT —— 1.20.1 里 SoundEvents 有一部分字段
     * 是 Holder 有一部分是 SoundEvent，这么写不管是哪种都能编译。
     */
    private static final SoundEvent TELEPORT_SOUND = SoundEvents.ENDERMAN_TELEPORT;

    private ResourceLocation dimension;
    private BlockPos pos;

    public RoomTeleportRequestPacket() {
    }

    public RoomTeleportRequestPacket(ResourceLocation dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.pos = buf.readBlockPos();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        // 1. 必须真的带着终端
        if (!hasTerminal(player)) {
            player.displayClientMessage(Component.literal("§c你身上没有收容间监控终端。"), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(ModItems.ROOM_TERMINAL.get())) {
            return;
        }

        // 2. 目标必须是登记过的信标
        ServerLevel current = player.serverLevel();
        RoomEntry entry = CompanyRoomData.get(current).findRoom(dimension, pos);
        if (entry == null) {
            player.displayClientMessage(Component.literal("§c该收容间信标已失效。"), true);
            return;
        }

        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                player.server, dimension, pos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            player.displayClientMessage(Component.literal(access
                    == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "§c该收容单元尚未扩建。" : "§c该收容单元所属部门尚未开放。"), true);
            return;
        }

        MinecraftServer server = player.server;
        ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            player.displayClientMessage(Component.literal("§c目标维度未加载。"), true);
            return;
        }

        BlockPos landing = findLanding(target, pos);

        // 出发点的瞬移声（给留在原地的人听）
        if (TELEPORT_SOUND != null) {
            current.playSound(null, player.getX(), player.getY(), player.getZ(),
                    TELEPORT_SOUND, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        player.getCooldowns().addCooldown(ModItems.ROOM_TERMINAL.get(), COOLDOWN_TICKS);
        player.teleportTo(target,
                landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();

        // 落地点的瞬移声（玩家自己听得到）
        if (TELEPORT_SOUND != null) {
            target.playSound(null, landing, TELEPORT_SOUND, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        player.displayClientMessage(Component.literal("§b[监控终端] §7已抵达：§f" + entry.resolveTitle()), true);
    }

    private static boolean hasTerminal(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ROOM_TERMINAL.get())) return true;
        }
        return player.getMainHandItem().is(ModItems.ROOM_TERMINAL.get())
                || player.getOffhandItem().is(ModItems.ROOM_TERMINAL.get());
    }

    /** 从信标位置往上找一个能站人的空位，最多找 6 格，找不到就直接站在信标上 */
    private static BlockPos findLanding(Level level, BlockPos marker) {
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos feet = marker.above(dy - 1);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return feet;
            }
        }
        return marker.above();
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
