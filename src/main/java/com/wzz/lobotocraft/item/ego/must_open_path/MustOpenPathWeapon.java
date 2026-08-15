package com.wzz.lobotocraft.item.ego.must_open_path;

import com.wzz.lobotocraft.entity.projectile.MustOpenPathAnchorProjectile;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.MathUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MustOpenPathWeapon extends BaseEgoWeapon {
    public static final String SET_ID = "must_open_path";
    private static final int MINIMUM_CHARGE_TICKS = 10;
    public static final int FULL_CHARGE_TICKS = 25;

    public MustOpenPathWeapon() {
        super(new ModTier.WeaponTier(), MathUtil.toDamageModifier(27),
                MathUtil.toSpeedModifier(0.6F),
                new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        super.onLeftClickEntity(stack, player, entity);
        if (!(entity instanceof LivingEntity target) || !canUseItem(player)) return true;
        boolean fullyCharged = isFullyChargedAttack(player);
        boolean hit = target.hurt(DamageHelper.getDamage(player, "red"), rollDamage(player));
        if (hit && !player.level().isClientSide) {
            // Normal swings are silent unless they are fully charged and the boundary is active.
            if (fullyCharged && MustOpenPathMechanics.isBoundaryActive(player)) {
                playRandomAttackSound(player.level(), target.blockPosition(), player.getRandom());
            }
            if (!target.isAlive()) {
                player.level().playSound(null, target.blockPosition(), ModSounds.MUST_OPEN_PATH_KILL.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return true;
    }

    public static float rollDamage(Player player) {
        int healthBonus = Math.max(0, (int) (player.getMaxHealth() / 50.0F)) * 2;
        return 27 + healthBonus + player.getRandom().nextInt(6);
    }

    public static boolean isFullyChargedAttack(Player player) {
        return player != null && BaseEgoWeapon.hasCurrentLeftClickAttack(player)
                && BaseEgoWeapon.currentLeftClickDamageMultiplier(player) >= 0.999F;
    }

    public static SoundEvent randomAttackSound(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> ModSounds.MUST_OPEN_PATH_ATTACK_0.get();
            case 1 -> ModSounds.MUST_OPEN_PATH_ATTACK_1.get();
            case 2 -> ModSounds.MUST_OPEN_PATH_ATTACK_2.get();
            default -> ModSounds.MUST_OPEN_PATH_ATTACK_3.get();
        };
    }

    public static void playRandomAttackSound(Level level, BlockPos position, RandomSource random) {
        level.playSound(null, position, randomAttackSound(random), SoundSource.PLAYERS,
                0.9F, 0.95F + random.nextFloat() * 0.1F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) return InteractionResultHolder.fail(stack);
        if (MustOpenPathMechanics.isDeployed(player)) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                MustOpenPathMechanics.returnToOrigin(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!player.isShiftKeyDown() || isOnCooldown(player, stack)
                || MustOpenPathMechanics.hasPendingAnchor(player)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof ServerPlayer player)) return;
        int chargedTicks = getUseDuration(stack) - timeLeft;
        if (chargedTicks < MINIMUM_CHARGE_TICKS || isOnCooldown(player, stack)
                || MustOpenPathMechanics.isDeployed(player)
                || MustOpenPathMechanics.hasPendingAnchor(player)) return;

        MustOpenPathAnchorProjectile anchor = new MustOpenPathAnchorProjectile(
                ModEntities.must_open_path_anchor.get(), player, level,
                player.serverLevel().dimension(), player.position());
        anchor.setItem(stack.copyWithCount(1));
        anchor.setFullyCharged(chargedTicks >= FULL_CHARGE_TICKS);
        anchor.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 0.0F);
        level.addFreshEntity(anchor);
        MustOpenPathMechanics.markPendingAnchor(player);
        if (chargedTicks >= FULL_CHARGE_TICKS) {
            level.playSound(null, player.blockPosition(), ModSounds.MUST_OPEN_PATH_THROW.get(),
                    SoundSource.PLAYERS, 1.6F, 1.0F);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public long getRemainingCooldown(Player player, ItemStack stack) {
        return MustOpenPathMechanics.remainingCooldown(player);
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> requirements = new LinkedHashMap<>();
        requirements.put("PrudenceLevel", 6);
        requirements.put("JusticeLevel", 6);
        requirements.put("EmployeeLevel", 5);
        return requirements;
    }

    @Override
    public String weaponName() {
        return SET_ID;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    protected boolean autoRegisterAttackAnim() {
        return false;
    }

    /** The supplied Blockbench asset is a native Java item model, not a GeckoLib model. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        EgoTooltipUtil.appendStandardDescription(
                tooltip,
                ClientInputUtil.isShiftPressed(),
                "tooltip.lobotocraft.must_open_path_weapon.info",
                new String[]{
                        "tooltip.lobotocraft.must_open_path_weapon.story_1",
                        "tooltip.lobotocraft.must_open_path_weapon.story_2",
                        "tooltip.lobotocraft.must_open_path_weapon.story_3"
                },
                new String[]{"tooltip.lobotocraft.must_open_path_weapon.damage"},
                new String[]{
                        "tooltip.lobotocraft.must_open_path_weapon.special_1",
                        "tooltip.lobotocraft.must_open_path_weapon.special_2"
                },
                new ChatFormatting[]{ChatFormatting.RED}
        );
    }
}
