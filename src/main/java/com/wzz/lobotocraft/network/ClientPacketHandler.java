package com.wzz.lobotocraft.network;

import com.wzz.lobotocraft.network.packet.ElevatorTeleportPacket;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {
    private static ElevatorCameraAnimation currentAnimation = null;

    public static void handleElevatorTeleport(ElevatorTeleportPacket packet) {
        currentAnimation = new ElevatorCameraAnimation(
                packet.getStartPos(),
                packet.getEndPos(),
                packet.getDuration()
        );
    }

    /**
     * 获取当前相机应该在的绝对Y坐标（如果动画正在播放）
     * @param partialTick 部分刻度，用于平滑插值
     * @return 相机Y坐标，如果没有动画则返回null
     */
    public static Double getCameraAbsoluteY(double partialTick) {
        if (currentAnimation != null) {
            return currentAnimation.getCurrentCameraY(partialTick);
        }
        return null;
    }

    public static void tick() {
        if (currentAnimation != null) {
            if (currentAnimation.tick()) {
                currentAnimation = null; // 动画完成
            }
        }
    }

    public static boolean isAnimating() {
        return currentAnimation != null;
    }

    @OnlyIn(Dist.CLIENT)
    private static class ElevatorCameraAnimation {
        private final Vec3 startPos;
        private final Vec3 endPos;
        private final int totalDuration;
        private int currentTick = 0;

        public ElevatorCameraAnimation(Vec3 startPos, Vec3 endPos, int duration) {
            int totalDuration1;
            this.startPos = startPos;
            this.endPos = endPos;
            totalDuration1 = Math.max(1, duration);
            if (duration <= 0) {
                totalDuration1 = 40;
            }
            this.totalDuration = totalDuration1;
        }

        /**
         * 获取当前相机应该在的绝对Y坐标
         * @param partialTick 部分刻度（0.0 - 1.0）
         */
        public double getCurrentCameraY(double partialTick) {
            if (currentTick > totalDuration) {
                return endPos.y; // 动画结束，返回最终位置
            }
            // 使用 partialTick 进行平滑插值
            double progress = Math.min(1.0f, (currentTick + partialTick) / totalDuration);
            double easedProgress = easeInOutCubic(progress);
            return lerp(startPos.y, endPos.y, easedProgress);
        }

        /**
         * @return true if animation is complete
         */
        public boolean tick() {
            currentTick++;
            return currentTick > totalDuration;
        }

        private double lerp(double start, double end, double progress) {
            return start + (end - start) * progress;
        }

        /**
         * 缓动函数：先加速后减速
         */
        private double easeInOutCubic(double t) {
            if (t < 0.5f) {
                return 4 * t * t * t;
            } else {
                double f = (2 * t) - 2;
                return 0.5f * f * f * f + 1;
            }
        }
    }
}