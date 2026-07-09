package com.wzz.lobotocraft.item.ego.punishing_bird;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.wzz.lobotocraft.item.ego.base.IAnimatablePerspective.Perspective.FIRST_PERSON_LEFT;
import static com.wzz.lobotocraft.item.ego.base.IAnimatablePerspective.Perspective.FIRST_PERSON_RIGHT;

public class PunishingBirdWeapon extends BaseEgoWeapon {

    private static final double ATTACK_RANGE = 10.0;
    private static final int USE_COOLDOWN_TICKS = 16;

    public PunishingBirdWeapon() {
        super(
                new Tier(),
                2,
                -2.0f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Set<Perspective> getAllowedPerspectives() {
        return EnumSet.of(FIRST_PERSON_RIGHT, FIRST_PERSON_LEFT);
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public String weaponName() {
        return "punishing_bird";
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

        // 检查攻击冷却
        if (stack.getOrCreateTag().getInt("UseTick") > 0) {
            player.displayClientMessage(Component.literal("§a冷却中,还剩:" + stack.getOrCreateTag().getInt("UseTick") + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }

        stack.getOrCreateTag().putInt("UseTick", USE_COOLDOWN_TICKS);
        player.playSound(ModSounds.PUNISHING_BIRD_WEAPON_ATTACK.get());

        if (!level.isClientSide) {
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.CRIT, 40, 0.1d, 10);
            LivingEntity target = EntityUtil.findLivingEntityInLookDirection(player, ATTACK_RANGE);

            if (target != null) {
                DamageSource damageSource = DamageHelper.getDamage(player, "red");
                target.hurt(damageSource, target.random.nextInt(3) + 1);
            }
        }

        triggerAttackAnimation(player, stack);
        return super.use(level, player, hand);
    }

    @Override
    protected String getAttackName() {
        return "animation.model.1";
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    /**
     * 注册枪械特有的换弹动画
     */
    @Override
    protected void registerAdditionalAnimations(software.bernie.geckolib.core.animation.AnimationController<BaseEgoWeapon> controller) {
        software.bernie.geckolib.core.animation.RawAnimation reloadAnimation =
                software.bernie.geckolib.core.animation.RawAnimation.begin().thenPlay("animation.model.2");
        controller.triggerableAnim("animation.model.2", reloadAnimation);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        components.add(Component.literal("§7虽然这只鸟的身材娇小，可它有着很恐怖的嘴巴。它不会宽恕那些阻碍自己前进的蠢货。"));
        components.add(Component.literal("§7这把武器配套的子弹有着尖锐的弹头，就像是一颗颗小尖牙。"));
        components.add(Component.literal("§7它会给目标造成巨大的痛苦。"));
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 4.0F;
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
