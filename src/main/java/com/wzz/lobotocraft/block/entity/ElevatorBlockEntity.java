package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ElevatorTeleportPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;

public class ElevatorBlockEntity extends BaseGeoBlockEntity {
    private static final RawAnimation ACTIVE_ANIMATION = RawAnimation.begin().thenLoop("dt-3");

    private int teleportDistance = 10;
    private boolean teleportUp = true;
    private int animationDuration = 40; // 动画持续时间(ticks)，40tick = 2秒

    private boolean isActive = false;
    private long activatedTick = -1;
    private static final long DELAY_TICKS = 30;

    public ElevatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElevatorBlockEntity be) {
        if (!be.isActive) return;
        long currentTick = level.getGameTime();
        if (currentTick - be.activatedTick >= DELAY_TICKS) {
            be.executeTeleport(level, pos);
            be.isActive = false;
            be.activatedTick = -1;
            // 传送完成，同步 isActive=false 到客户端
            be.syncToClient();
        }
    }

    public void onStep(LivingEntity entity) {
        Level level = entity.level();
        if (level.isClientSide()) return;
        if (this.isActive) return;

        this.isActive = true;
        this.activatedTick = level.getGameTime();
        syncToClient();
    }

    private void executeTeleport(Level level, BlockPos pos) {
        AABB detectionBox = new AABB(
                pos.getX(), pos.getY() + 1, pos.getZ(),
                pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1
        ).inflate(0.3, 0, 0.3);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, detectionBox);
        for (LivingEntity entity : entities) {
            double targetY = teleportUp
                    ? entity.getY() + teleportDistance
                    : entity.getY() - teleportDistance;
            if (entity instanceof ServerPlayer serverPlayer) {
                Vec3 startPos = entity.position().add(0, 1, 0);
                Vec3 endPos = new Vec3(entity.getX(), targetY + 1, entity.getZ());
                MessageLoader.getLoader().sendToPlayer(
                        serverPlayer,
                        new ElevatorTeleportPacket(startPos, endPos, animationDuration)
                );
                serverPlayer.teleportTo(entity.getX(), targetY, entity.getZ());
            } else {
                entity.teleportTo(entity.getX(), targetY, entity.getZ());
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isActive", this.isActive);
        tag.putInt("teleportDistance", this.teleportDistance);
        tag.putBoolean("teleportUp", this.teleportUp);
        tag.putInt("animationDuration", this.animationDuration);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.isActive = tag.getBoolean("isActive");
            this.teleportDistance = tag.getInt("teleportDistance");
            this.teleportUp = tag.getBoolean("teleportUp");
            this.animationDuration = tag.getInt("animationDuration");
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            BlockState state = level.getBlockState(getBlockPos());
            level.sendBlockUpdated(getBlockPos(), state, state, 2);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("teleportDistance", this.teleportDistance);
        tag.putBoolean("teleportUp", this.teleportUp);
        tag.putInt("animationDuration", this.animationDuration);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.teleportDistance = tag.getInt("teleportDistance");
        this.teleportUp = tag.getBoolean("teleportUp");
        // 使用默认值40，如果NBT中没有则保持默认
        if (tag.contains("animationDuration")) {
            this.animationDuration = tag.getInt("animationDuration");
        } else {
            this.animationDuration = 40; // 确保有默认值
        }
    }

    public int getTeleportDistance() { return this.teleportDistance; }
    public void setTeleportDistance(int distance) { this.teleportDistance = distance; }
    public boolean isTeleportUp() { return this.teleportUp; }
    public void setTeleportUp(boolean up) { this.teleportUp = up; }
    public int getAnimationDuration() { return this.animationDuration; }
    public void setAnimationDuration(int duration) { this.animationDuration = duration; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ElevatorBlockEntity> state) {
        if (this.isActive) {
            state.getController().setAnimation(ACTIVE_ANIMATION);
            return PlayState.CONTINUE;
        } else {
            state.getController().stop();
            return PlayState.STOP;
        }
    }
}