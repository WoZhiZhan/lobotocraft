package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.LockInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.WeakHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerControlLock {

    /**
     * 当前锁定状态
     */
    private static final WeakHashMap<Player, LockData> lockedPlayers = new WeakHashMap<>();

    /**
     * 开始锁定玩家
     */
    public static void lock(Player player, LivingEntity target, double speed, double range) {
        lockedPlayers.put(player, new LockData(target, speed, range));
    }

    /**
     * 解除锁定
     */
    public static void unlock(Player player) {
        lockedPlayers.remove(player);
    }

    /**
     * 是否锁定
     */
    public static boolean isLocked(Player player) {
        return lockedPlayers.containsKey(player);
    }

    /**
     * 每 tick 处理
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (!lockedPlayers.containsKey(player)) return;

        LockData data = lockedPlayers.get(player);
        LivingEntity target = data.target;
        if (target == null || target.isRemoved() || player.isDeadOrDying() || target.isDeadOrDying()) {
            unlock(player);
            return;
        }
        moveToTarget(player, target, data.speed, data.range);
        if (player.level.isClientSide) {
            lookAtTarget(player, target, 5f, 5f); // 旋转速度可调
        } else if (player instanceof ServerPlayer serverPlayer) {
            MessageLoader.getLoader().sendToPlayer(serverPlayer, new LockInputPacket());
        }
    }

    /**
     * 自动旋转玩家朝向目标
     */
    private static void lookAtTarget(Player player, LivingEntity target, float yawSpeed, float pitchSpeed) {
        double dx = target.getX() - player.getX();
        double dy = (target.getEyeY()) - (player.getEyeY());
        double dz = target.getZ() - player.getZ();

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

        player.setYRot(Mth.rotateIfNecessary(player.getYRot(), yaw, yawSpeed));
        player.setXRot(Mth.rotateIfNecessary(player.getXRot(), pitch, pitchSpeed));
    }

    /**
     * 自动移动玩家朝向目标
     */
    private static void moveToTarget(Player player, LivingEntity target, double speed, double range) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < range) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            return;
        }

        double vx = dx / distance * speed;
        double vz = dz / distance * speed;

        player.setDeltaMovement(vx, player.getDeltaMovement().y, vz);
        player.hasImpulse = true;
    }

    /**
     * 内部存储数据
     */
    private record LockData(LivingEntity target, double speed, double range) {
    }
}