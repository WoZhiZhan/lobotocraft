package com.wzz.lobotocraft.item.ego.crumbling_armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;
import java.util.UUID;

public class CrumblingArmorWeapon extends BaseEgoWeapon {
    private static final UUID REACH_UUID = UUID.fromString("74209330-fbb3-4184-9f68-4405fee4f544");
    private static final String TAG_CHARGING = "IaiCharging";
    private static final String TAG_CHARGE_START = "IaiChargeStartTime";
    private static final long CHARGE_DURATION = 1250;
    public CrumblingArmorWeapon() {
        super(
                new Tier(),
                MathUtil.toDamageModifier(8),
                MathUtil.toSpeedModifier(1.0f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> originalModifiers = super.getAttributeModifiers(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(originalModifiers);
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, weaponName() + "_reach",
                            1.0D, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return originalModifiers;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public String weaponName() {
        return "crumbling_armor";
    }

    @Override
    protected void registerAdditionalAnimations(AnimationController<BaseEgoWeapon> controller) {
        controller.triggerableAnim("t1", RawAnimation.begin().thenPlay("t1"));
        controller.triggerableAnim("t2", RawAnimation.begin().thenPlay("t2"));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        target.hurt(DamageHelper.getDamage(player, "blue"), 8f + (EntityUtil.addMaxHealthPercentageDamage(
                target, 0.01f, 0.03f, 3f
        )));
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_CHARGING, true);
        tag.putLong(TAG_CHARGE_START, world.getGameTime());
        triggerAnimation(player, stack, "t1");
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack p_41454_) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity entity, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (world.isClientSide()) return;

        CompoundTag tag = stack.getTag();
        if (tag == null) {
            player.stopUsingItem();
            cancelCharge(player, stack);
            return;
        }

        boolean wasCharging = tag.getBoolean(TAG_CHARGING);
        long startTick = tag.getLong(TAG_CHARGE_START);

        clearChargeTag(stack);
        player.stopUsingItem();

        if (!wasCharging) {
            // 没在蓄力也保证动画回到手持状态
            cancelCharge(player, stack);
            return;
        }

        long chargeTicks = world.getGameTime() - startTick;
        long requiredTicks = CHARGE_DURATION / 50; // 1250ms = 25 ticks

        if (chargeTicks >= requiredTicks) {
            executeIaiSlash(world, player, stack);
        } else {
            // 蓄力不足1.25秒：直接打断，武器回到手持(默认)状态，不停留在蓄力动画
            player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.0F);
            cancelCharge(player, stack);
        }
    }

    /**
     * 蓄力被打断时（切换物品、丢弃、打开背包、死亡等）也要还原动画，
     * 否则 t1 会一直停在最后一帧。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_CHARGING)) return;

        boolean stillCharging = player.isUsingItem() && player.getUseItem() == stack && selected;
        if (!stillCharging) {
            player.stopUsingItem();
            cancelCharge(player, stack);
        }
    }

    /**
     * 取消蓄力，重置动画到默认（手持）状态
     * 注意：必须用 stopTriggeredAnim（BaseEgoWeapon#stopAnimation(player, stack, name)），
     * 它会把“停止动画”同步给客户端；只在服务端 forceAnimationReset 是无效的，
     * 客户端会一直保持在 t1 的最后一帧。
     */
    private void cancelCharge(Player player, ItemStack stack) {
        clearChargeTag(stack);
        stopAnimation(player, stack, "t1");
        stopAnimation(player, stack, "t2");
        stopAnimation(player, stack);
    }

    /**
     * 清除蓄力 NBT
     */
    private void clearChargeTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_CHARGING);
            tag.remove(TAG_CHARGE_START);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        boolean oldCharging = oldStack.hasTag() && oldStack.getTag().getBoolean(TAG_CHARGING);
        boolean newCharging = newStack.hasTag() && newStack.getTag().getBoolean(TAG_CHARGING);
        return oldCharging != newCharging;
    }

    private void executeIaiSlash(Level world, Player player, ItemStack stack) {
        triggerAnimation(player, stack, "t2");
        player.getCooldowns().addCooldown(this, 20);
        Vec3 targetPos = findTeleportPosition(player, 6);
        List<LivingEntity> targets = EntityUtil.findAllEntitiesInLookDirection(player, 6, 2, LivingEntity.class);
        for (LivingEntity target : targets) {
            target.hurt(DamageHelper.getDamage(player, "blue"),
                    20f + EntityUtil.addMaxHealthPercentageDamage(target, 0.03f, 0.05f, 10f));
        }
        ParticleUtil.spawnLineParticlesSpread(world, player, ParticleTypes.END_ROD, 50, 0.1d, 6, 1);
        player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        ParticleUtil.spawnLineParticlesSpread(world, player, ParticleTypes.END_ROD, 10, 0.1d, 0.6D, 1);
        world.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.4F);
    }

    private Vec3 findTeleportPosition(Player player, double maxDistance) {
        Vec3 startPos = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 lookVec = player.getLookAngle().normalize();
        Level world = player.level();

        double step = 0.3;
        double traveled = 0;
        Vec3 lastValidPos = player.position();

        while (traveled < maxDistance) {
            Vec3 checkPos = startPos.add(lookVec.scale(traveled));
            BlockPos feetPos = BlockPos.containing(checkPos.x, checkPos.y - player.getEyeHeight(), checkPos.z);
            BlockPos headPos = feetPos.above();

            // 检查碰撞
            if (!world.getBlockState(feetPos).isAir() && world.getBlockState(feetPos).isSuffocating(world, feetPos)) {
                return lastValidPos;
            }
            if (!world.getBlockState(headPos).isAir() && world.getBlockState(headPos).isSuffocating(world, headPos)) {
                return lastValidPos;
            }

            lastValidPos = new Vec3(checkPos.x, feetPos.getY(), checkPos.z);
            traveled += step;
        }

        return lastValidPos;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7这是一把古老的武士刀。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7就同那副盔甲一样，如果这把刀落在了懦夫的手里，它便一点威力也没有。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※这把武器普通攻击造成蓝色伤害时，额外附带对方最大生命值3%的蓝色伤害（最高3点）。特殊攻击造成伤害时，额外附带对方最大生命值5%的蓝色伤害"));
        }
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 3.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}