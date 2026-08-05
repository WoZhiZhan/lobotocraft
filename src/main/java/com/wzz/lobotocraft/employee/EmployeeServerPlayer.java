package com.wzz.lobotocraft.employee;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import com.wzz.lobotocraft.work.WorkManager;

public final class EmployeeServerPlayer extends ServerPlayer {
    private final EmployeeRecord employeeRecord;
    private long navigationResumeGameTime;

    public EmployeeServerPlayer(ServerLevel level, GameProfile profile, EmployeeRecord employeeRecord) {
        super(level.getServer(), level, profile);
        this.employeeRecord = employeeRecord;
        this.connection = new EmployeePacketListener(level.getServer(), this);
        getPersistentData().putBoolean("lobotocraft_ai_employee", true);
        maintainFullFood();
    }

    public EmployeeRecord employeeRecord() {
        return employeeRecord;
    }

    @Override
    public RemoteChatSession getChatSession() {
        return null;
    }

    @Override
    public void setChatSession(RemoteChatSession chatSession) {
        // Synthetic employees cannot sign or receive chat. Keeping this state
        // permanently absent makes every vanilla player-info path encode a
        // nullable false instead of a public-key payload.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        maintainFullFood();
    }

    @Override
    public void tick() {
        maintainFullFood();
        super.tick();
        maintainFullFood();
    }

    @Override
    public void causeFoodExhaustion(float amount) {
        // AI employees do not participate in the survival hunger loop.
    }

    private void maintainFullFood() {
        getFoodData().setFoodLevel(20);
        getFoodData().setSaturation(20.0F);
        getFoodData().setExhaustion(0.0F);
    }

    public void setNavigationInput(float yaw, float forward, boolean jump) {
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setXRot(0.0F);
        xxa = 0.0F;
        zza = Math.max(-1.0F, Math.min(1.0F, forward));
        jumping = jump;
        setSprinting(forward > 0.0F);
    }

    public void clearNavigationInput() {
        xxa = 0.0F;
        zza = 0.0F;
        jumping = false;
        setSprinting(false);
        normalizeHeadPose();
    }

    public void faceTarget(Vec3 target) {
        lookAt(EntityAnchorArgument.Anchor.EYES, target);
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
    }

    private void normalizeHeadPose() {
        setXRot(0.0F);
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
    }

    /**
     * Apply the vanilla knockback calculation and briefly yield navigation control so the
     * physics impulse is not overwritten by movement input on the next server tick.
     */
    @Override
    public void knockback(double strength, double x, double z) {
        if (WorkManager.isPlayerWorking(this)) {
            setDeltaMovement(Vec3.ZERO);
            hasImpulse = false;
            hurtMarked = false;
            return;
        }
        double applied = strength * (1.0D - getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (applied <= 0.0D) return;

        Vec3 direction = new Vec3(x, 0.0D, z);
        if (direction.lengthSqr() < 1.0E-7D) {
            direction = new Vec3(0.01D, 0.0D, 0.0D);
        }
        direction = direction.normalize().scale(applied);
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(
                movement.x * 0.5D - direction.x,
                onGround() ? Math.min(0.4D, movement.y * 0.5D + applied) : movement.y,
                movement.z * 0.5D - direction.z
        );
        hasImpulse = true;
        hurtMarked = true;
        clearNavigationInput();
        navigationResumeGameTime = Math.max(navigationResumeGameTime, serverLevel().getGameTime() + 10L);
    }

    public boolean isNavigationInterrupted() {
        return serverLevel().getGameTime() < navigationResumeGameTime;
    }

    @Override
    public void displayClientMessage(Component component, boolean actionBar) {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        absMoveTo(x, y, z, getYRot(), getXRot());
        serverLevel().getChunkSource().move(this);
    }
}
