package com.wzz.lobotocraft.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 微笑的尸山 —— 特殊攻击落点处的"魔法阵冲击波"纯视觉实体。
 *
 * 逻辑很轻：不参与物理/碰撞/存档，只负责在世界里存在 {@link #LIFETIME} tick,
 * 由客户端渲染器 {@code SmilingCorpseMountainShockWaveRenderer} 按 tickCount
 * 循环播放 tex_0~tex_6 共 {@link #LOOPS} 次并逐渐放大,到时自动 discard。
 */
public class EntitySmilingCorpseMountainShockWave extends Entity {

    /** 帧数(tex_0 .. tex_6) */
    public static final int FRAMES = 7;
    /** 每帧停留的 tick 数 */
    public static final int FRAME_TICKS = 3;
    /** 整段动画循环次数 */
    public static final int LOOPS = 3;
    /** 实体存活总时长 = 7 * 3 * 3 = 63 tick (~3.15s) */
    public static final int LIFETIME = FRAMES * FRAME_TICKS * LOOPS;

    public EntitySmilingCorpseMountainShockWave(EntityType<EntitySmilingCorpseMountainShockWave> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noCulling = true;
    }

    @Override
    public void tick() {
        super.tick();
        // 视觉实体不需要移动
        this.setDeltaMovement(0, 0, 0);
        if (!level().isClientSide && this.tickCount >= LIFETIME) {
            this.discard();
        }
    }

    /** 纯视觉实体不参与存档 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        // 无需同步的自定义数据
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
