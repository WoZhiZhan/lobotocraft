package com.wzz.lobotocraft.item.ego.helper;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class HelperWeapon extends BaseEgoWeapon implements AiSpecialEgoWeapon {
    private static final double ATTACK_RANGE = 4.0;
    @Override public double aiSpecialAttackRange() { return ATTACK_RANGE; }
    private static final UUID REACH_UUID = UUID.fromString("4abcfd08-d9d9-4f67-9794-4edaab555777");
    public HelperWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(2),
                MathUtil.toSpeedModifier(1.6f),
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
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        AtomicReference<Float> damage = new AtomicReference<>(2f);
        if (EgoArmorHelper.isFullEGO(player, "helper")) {
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> damage.updateAndGet(v -> v + data.getJusticeLevel()));
        }
        target.hurt(DamageHelper.getDamage(player, "red"), damage.get());
        return true;
    }

    @Override
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 20;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (isOnCooldown(player, stack)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.ego.cooldown_ticks",
                    getRemainingCooldown(player, stack)), true);
            return InteractionResultHolder.pass(stack);
        }
        startCooldown(player, stack);
        if (!level.isClientSide) {
            SoundUtil.playSound(level, player, ModSounds.HELPER_WEAPON.get());
            triggerAttackAnimation(player, stack);
            for (LivingEntity target : EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE)) {
                DamageTimer.addNewTimer(target, DamageHelper.getDamage(player, "red"), player);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 2);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public String weaponName() {
        return "helper";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 6; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.helper_weapon.story." + index);
                if (index < 6) EgoTooltipUtil.blank(p_41423_);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details");
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.helper_weapon.detail");
        }
    }

    public static class DamageTimer extends TimerEntry<LivingEntity> {
        private final Player attacker;
        private final DamageSource damageSource;
        private int attackCount = 0;
        private static final int MAX_ATTACKS = 6;

        private DamageTimer(DamageSource damageSource, Player attacker) {
            this.damageSource = damageSource;
            this.attacker = attacker;
        }

        public static void addNewTimer(LivingEntity living, DamageSource damageSource, Player attacker) {
            DamageTimer timer = new DamageTimer(damageSource, attacker);
            timer.addSkillTimer(living, 170, 580, 10, true);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            attackCount = 1;
            performAttack(entity);
        }

        @Override
        public void onRunning(@NotNull LivingEntity entity) {
            if (attackCount < MAX_ATTACKS) {
                attackCount++;
                performAttack(entity);
            }
        }

        private void performAttack(LivingEntity entity) {
            if (entity.isAlive() && !entity.isRemoved()) {
                if (attacker != null) {
                    if (EgoArmorHelper.isFullEGO(attacker, "helper")) {
                        attacker.heal(1f);
                    }
                }
                AtomicReference<Float> damage = new AtomicReference<>(2f);
                if (EgoArmorHelper.isFullEGO(attacker, "helper")) {
                    attacker.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> damage.updateAndGet(v -> v + data.getJusticeLevel()));
                }
                EntityUtil.clearHurtTime(entity, () -> entity.hurt(damageSource, damage.get()));
            }
        }
    }
}
