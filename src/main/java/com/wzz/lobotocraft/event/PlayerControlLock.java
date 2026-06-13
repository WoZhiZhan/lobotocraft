package com.wzz.lobotocraft.event;

import net.minecraft.client.player.LocalPlayer;
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
        if (player.level.isClientSide && player instanceof LocalPlayer local) {
            lockInput(local);
            lookAtTarget(local, target, 5f, 5f); // 旋转速度可调
        }
    }

    /**
     * 阻止玩家移动和跳跃
     */
    private static void lockInput(LocalPlayer player) {
        player.input.leftImpulse = 0;
        player.input.forwardImpulse = 0;
        player.input.jumping = false;
        player.input.shiftKeyDown = false;
        player.input.up = false;
        player.input.down = false;
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