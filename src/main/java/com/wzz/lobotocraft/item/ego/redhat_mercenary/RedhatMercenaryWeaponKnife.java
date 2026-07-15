package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DotHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * 赤色佣兵 —— 短刀
 * 动画：
 *   attack —— 基类的攻击动画（triggerAttackAnimation）
 *   "1"    —— 拔刀：从别的物品切换到本武器时触发（thenPlayAndHold，保持在持刀姿势）
 *   "2"    —— 收刀：从本武器切换到别的物品时触发
 * 暴怒（生命值 < 50%）：
 *   • 伤害额外 +50%
 *   • 攻击时同步伤害身前 2×3 范围内的所有生物
 *   • 无差别：范围伤害不区分玩家/队友
 *   • 持有者周围持续红色粒子
 */
public class RedhatMercenaryWeaponKnife extends RedhatMercenaryWeapon {

    /** 暴怒阈值 */
    private static final float FURY_HEALTH_RATIO = 0.5f;
    /** 暴怒伤害倍率 */
    private static final float FURY_DAMAGE_MULTIPLIER = 1.5f;
    /** 暴怒时的范围伤害：身前 3 格、宽 2 格 */
    private static final int AOE_RANGE = 3;
    private static final int AOE_WIDTH = 2;
    /** 暴怒音效间隔（tick），避免每 tick 都响 */
    private static final int FURY_SOUND_INTERVAL = 60;
    /** 暴怒粒子间隔（tick） */
    private static final int FURY_PARTICLE_INTERVAL = 4;

    /** 上一 tick 是否被手持，用来判断“切进来 / 切出去” */
    private static final String TAG_SELECTED = "redhat_knife_selected";
    /** 暴怒音效冷却 */
    private static final String TAG_FURY_SOUND_CD = "redhat_knife_fury_sound_cd";
    private static final int REDRAW_ON_JOIN_TICK = 20;

    @Override
    protected void registerAdditionalAnimations(AnimationController<BaseEgoWeapon> controller) {
        controller.triggerableAnim("1", RawAnimation.begin().thenPlayAndHold("1"));
        controller.triggerableAnim("attack", RawAnimation.begin().thenPlayAndHold("attack"));
        controller.triggerableAnim("2", RawAnimation.begin().thenPlay("2"));
    }

    /** 是否处于暴怒状态 */
    public static boolean isFury(Player player) {
        return player.getHealth() <= player.getMaxHealth() * FURY_HEALTH_RATIO;
    }

    @Override
    protected boolean autoRegisterAttackAnim() {
        return false;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!canUseItem(player)) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }

        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        stopAnimation(player, stack, "attack");
        triggerAttackAnimation(player, stack);

        boolean fury = isFury(player);
        float damage = 11 + player.getRandom().nextInt(3);
        if (fury) {
            damage *= FURY_DAMAGE_MULTIPLIER;
        }

        DamageSource source = target.damageSources().playerAttack(player);
        target.hurt(source, damage);
        player.playSound(ModSounds.REDHAT_MERCENARY_WEAPON_KNIFE.get());
        if (fury && !player.level.isClientSide) {
            hurtEntitiesInFront(player, target, damage);
            ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.RED.get(), 15, 0.2D);
            if (EgoArmorHelper.isFullEGO(player, "redhat_mercenary")) {
                DotHelper.applyDot("redhat_mercenary", player, target, 2f, "red", 20, 300, 1);
            }
        }
        return true;
    }

    /** 暴怒：身前 2×3 范围内的所有生物一起吃同样的伤害（无差别，不排除玩家/队友） */
    private void hurtEntitiesInFront(Player player, LivingEntity mainTarget, float damage) {
        for (LivingEntity other : EntityUtil.findAllEntitiesInLookDirection(
                player, AOE_RANGE, AOE_WIDTH, LivingEntity.class)) {
            if (other == player || other == mainTarget) continue;
            if (!other.isAlive()) continue;

            // 清掉无敌帧，保证同一次挥砍里每个目标都能吃到伤害
            other.invulnerableTime = 0;
            other.hurt(other.damageSources().playerAttack(player), damage);
        }
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotIndex, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotIndex, isSelected);
        if (level.isClientSide() || !(entity instanceof Player player)) return;

        CompoundTag tag = stack.getOrCreateTag();

        // 拔刀 / 收刀：手持状态发生变化的那一 tick 触发
        boolean wasSelected = tag.getBoolean(TAG_SELECTED);
        if (isSelected != wasSelected) {
            tag.putBoolean(TAG_SELECTED, isSelected);
            if (isSelected) {
                stopAnimation(player, stack, "2");
                triggerAnimation(player, stack, "1");   // 拔刀
            } else {
                stopAnimation(player, stack, "1");
                stopAnimation(player, stack, "attack");
                triggerAnimation(player, stack, "2");   // 收刀
            }
        } else if (isSelected && player.tickCount == REDRAW_ON_JOIN_TICK) {
            // 重进游戏/重生：NBT 里还是"手持中"，但客户端动画状态已经没了，补一次拔刀
            stopAnimation(player, stack, "2");
            triggerAnimation(player, stack, "1");
        }

        if (isSelected) {
            if (isFury(player)) {
                int cooldown = tag.getInt(TAG_FURY_SOUND_CD);
                if (cooldown <= 0) {
                    player.playSound(ModSounds.REDHAT_MERCENARY_WEAPON_FURY.get());
                    tag.putInt(TAG_FURY_SOUND_CD, FURY_SOUND_INTERVAL);
                } else {
                    tag.putInt(TAG_FURY_SOUND_CD, cooldown - 1);
                }

                if (player.tickCount % FURY_PARTICLE_INTERVAL == 0) {
                    ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.RED_LIGHT.get(), 6, 0.15D);
                }
            } else {
                tag.remove(TAG_FURY_SOUND_CD);
            }
        }
    }

    @Override
    protected String postFix() {
        return "knife";
    }
}