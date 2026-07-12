package com.wzz.lobotocraft.item.ego.leticia;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LeticiaWeapon extends BaseEgoWeapon {
    private static final double ATTACK_RANGE = 25.0;
    private static final int USE_COOLDOWN_TICKS = 20;
    public LeticiaWeapon() {
        super(
                new Tier(),
                MathUtil.toDamageModifier(6),
                MathUtil.toSpeedModifier(1f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            stack.getOrCreateTag().putInt("UseTick", stack.getOrCreateTag().getInt("UseTick") - 1);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            player.displayClientMessage(Component.literal("§a冷却中,还剩:" + stack.getOrCreateTag().getInt("UseTick") + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        stack.getOrCreateTag().putInt("UseTick", USE_COOLDOWN_TICKS);
        if (!level.isClientSide) {
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.CRIT, 50, 0.1d, ATTACK_RANGE);
            SoundUtil.playSound(level, player, ModSounds.PUNISHING_BIRD_WEAPON_ATTACK.get());
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);
            if (target != null) {
                float damage = 6f;
                boolean hasCurio = CuriosUtil.hasCurios(player, ModItems.LETICIA_CURIO.get());
                double blackResistance = target.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get()).getValue();
                if (blackResistance < 1.0D) {
                    damage = 12f;
                    if (hasCurio) {
                        // 提高自己10%移动速度（持续10秒，刷新持续时间）
                        SpeedEffectTimer playerTimer = new SpeedEffectTimer(0.1);
                        playerTimer.addSkillTimer(player, 0, 10000, 1, true);
                    }
                } else if (blackResistance > 1.0D && hasCurio) {
                    // 减少对方25%移动速度（持续10秒，刷新持续时间）
                    SpeedEffectTimer targetTimer = new SpeedEffectTimer(-0.25);
                    targetTimer.addSkillTimer(target, 0, 10000, 1, true);
                }
                DamageSource damageSource = DamageHelper.getDamage(player, "black");
                target.hurt(damageSource, damage);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("TemperanceLevel", 2);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "leticia";
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7※这把武器会对高抗性的目标造成更高的伤害。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7掌握并使用这杆步枪需要时间的磨合，"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7粗糙的结构设计令它的外表有些老旧，可它仍有不可忽视的威力。"));
            p_41423_.add(Component.literal(""));
            p_41423_.add(Component.literal("§7枪上那些小巧的配件就如同少女心中对幸福的憧憬。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        } else {
            p_41423_.add(Component.literal("§6※玩家对黑色伤害抗性低于1.0的目标造成双倍伤害。"));
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

    public static class SpeedEffectTimer extends TimerEntry<LivingEntity> {
        private final double speedChange;
        private final UUID modifierUUID = UUID.fromString("a8d4bc56-d729-4bb4-8208-4831d2217864");

        public SpeedEffectTimer(double speedChange) {
            this.speedChange = speedChange;
            this.setRequireMainThread(true);
        }

        @Override
        public void onStart(@NotNull LivingEntity entity) {
            applySpeedModifier(entity, speedChange);
        }

        @Override
        public void onEnd(@NotNull LivingEntity entity) {
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(modifierUUID);
            }
        }

        private void applySpeedModifier(LivingEntity entity, double speedChange) {
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(modifierUUID);
                attribute.addTransientModifier(new AttributeModifier(
                        modifierUUID,
                        "speed_effect_modifier",
                        speedChange,
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                ));
            }
        }
    }
}