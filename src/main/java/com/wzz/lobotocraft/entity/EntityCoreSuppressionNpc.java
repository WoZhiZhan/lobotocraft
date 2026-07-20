package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenCoreSuppressionScreenPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

public class EntityCoreSuppressionNpc extends PathfinderMob {
    public EntityCoreSuppressionNpc(EntityType<? extends EntityCoreSuppressionNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(1, new RandomLookAroundGoal(this));
    }

    public CoreSuppressionType getCoreSuppressionType() {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(getType());
        return key == null ? null : CoreSuppressionType.byId(key.getPath());
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        CoreSuppressionType type = getCoreSuppressionType();
        if (type == null) return InteractionResult.FAIL;
        String denial = CoreSuppressionManager.getStartDenialReason(serverPlayer, type);
        if (denial != null) {
            serverPlayer.sendSystemMessage(Component.literal("§c" + denial));
            return InteractionResult.CONSUME;
        }

        MessageLoader.getLoader().sendToPlayer(serverPlayer,
                new OpenCoreSuppressionScreenPacket(type.ordinal(), getId()));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public @NotNull Component getName() {
        CoreSuppressionType type = getCoreSuppressionType();
        return type == null ? super.getName() : Component.literal(type.getDisplayName());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
