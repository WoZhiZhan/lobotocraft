package com.wzz.lobotocraft.entity.ai;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class AttackAI {
    public static void doAttack(Player player) {
        if (player.isDeadOrDying())
            return;
        player.walkDist = 0.0F;
        LivingEntity targetEntity = null;
        double closestDistance = Double.MAX_VALUE;
        long currentTime = player.level().getGameTime();
        float attackCooldown = player.getAttackStrengthScale(0.5F);
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains("lastAttackTime")) {
            persistentData.putLong("lastAttackTime", 0);
        }

        if (!persistentData.contains("retreatTime")) {
            persistentData.putLong("retreatTime", 0);
        }
        long retreatTime = persistentData.getLong("retreatTime");

        for (Entity entity : player.level().getEntities(player,
                player.getBoundingBox().inflate(10),
                e -> e instanceof LivingEntity && e != player)) {

            LivingEntity livingEntity = (LivingEntity)entity;
            double distance = player.distanceTo(livingEntity);
            if (distance < closestDistance && livingEntity.getHealth() > 0) {
                closestDistance = distance;
                targetEntity = livingEntity;
            }
        }

        if (targetEntity != null) {
            double deltaX = targetEntity.getX() - player.getX();
            double deltaY = (targetEntity.getEyeY()) - (player.getEyeY());
            double deltaZ = targetEntity.getZ() - player.getZ();

            double angleYaw = Mth.atan2(deltaZ, deltaX) * (180 / Math.PI) - 90;
            double distanceXZ = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double anglePitch = -Mth.atan2(deltaY, distanceXZ) * (180 / Math.PI);

            double sprintSpeed = 0.4;

            if (player.distanceTo(targetEntity) < 3.2D) {
                if (currentTime - retreatTime >= 40) {
                    persistentData.putLong("retreatTime", currentTime);
                    player.setDeltaMovement(
                            (-Mth.cos((float)Math.toRadians(angleYaw + 90)) * sprintSpeed) * 1.5,
                            player.getDeltaMovement().y,
                            (-Mth.sin((float)Math.toRadians(angleYaw + 90)) * sprintSpeed) * 1.5
                    );
                } else {
                    player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                }
            } else {
                player.setDeltaMovement(
                        Mth.cos((float)Math.toRadians(angleYaw + 90)) * sprintSpeed,
                        player.getDeltaMovement().y,
                        Mth.sin((float)Math.toRadians(angleYaw + 90)) * sprintSpeed
                );
            }

            // 设置玩家朝向
            player.setYRot((float)angleYaw);
            player.setXRot((float)anglePitch);
            player.yHeadRot = player.getYRot();
            player.yBodyRot = player.getYRot();

            // 攻击逻辑
            if (player.distanceTo(targetEntity) < 7.0D) {
                if (attackCooldown >= 1.0F) { // 攻击冷却完成(100%)
                    player.swing(InteractionHand.MAIN_HAND);

                    // 重置攻击冷却(相当于原版的攻击动作)
                    player.resetAttackStrengthTicker();

                    if (player.getRandom().nextInt(101) <= 50 && player.onGround()) {
                        player.jumpFromGround();
                    }

                    targetEntity.hurt(player.damageSources().mobAttack(player), 4.0f);
                    player.attack(targetEntity);
                }
            }

            // 自动进食逻辑
            if (player.getFoodData().needsFood()) {
                for (ItemStack itemStack : player.getInventory().items) {
                    if (itemStack.getItem().isEdible()) {
                        itemStack.shrink(1);
                        player.heal(4);

                        if (player instanceof ServerPlayer serverPlayer) {
                            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, itemStack);
                        }

                        player.getFoodData().eat(itemStack.getItem(), itemStack, player);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS,
                                0.5F, player.level().random.nextFloat() * 0.1F + 0.9F);

                        player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
                    }
                }
            }

            // 跳跃逻辑
            if (player.horizontalCollision) {
                player.jumpFromGround();
            }

            // 躲避箭矢逻辑
            for (AbstractArrow arrow : player.level().getEntitiesOfClass(AbstractArrow.class,
                    player.getBoundingBox().inflate(5))) {

                Vec3 predictedPos = arrow.position().add(arrow.getDeltaMovement().scale(5));
                double distanceToArrow = player.distanceToSqr(predictedPos);

                if (distanceToArrow < 16.0D) { // 4^2
                    double dodgeAngle = Mth.atan2(arrow.getDeltaMovement().z, arrow.getDeltaMovement().x) + Math.PI / 2;
                    player.setDeltaMovement(
                            Mth.cos((float)dodgeAngle) * sprintSpeed,
                            player.getDeltaMovement().y,
                            Mth.sin((float)dodgeAngle) * sprintSpeed
                    );
                }
            }
        }
    }
}
