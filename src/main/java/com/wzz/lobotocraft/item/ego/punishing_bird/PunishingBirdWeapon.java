package com.wzz.lobotocraft.item.ego.punishing_bird;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.wzz.lobotocraft.item.ego.base.IAnimatablePerspective.Perspective.FIRST_PERSON_LEFT;
import static com.wzz.lobotocraft.item.ego.base.IAnimatablePerspective.Perspective.FIRST_PERSON_RIGHT;

public class PunishingBirdWeapon extends BaseEgoWeapon {

    private static final double ATTACK_RANGE = 10.0;

    public PunishingBirdWeapon() {
        super(
                new ModTier.WeaponTier(),
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
    protected int getCooldownTicks(Player player, ItemStack stack) {
        return 16;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isOnCooldown(player, stack)) {
            player.displayClientMessage(
                    Component.literal("§a冷却中,还剩:" + getRemainingCooldown(player, stack) + " tick"), true);
            return InteractionResultHolder.pass(stack);
        }
        startCooldown(player, stack);
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
}
