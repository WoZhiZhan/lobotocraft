package com.wzz.lobotocraft.item.ego.must_open_path;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.event.listener.MentalValueEventHandler;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;

public final class MustOpenPathCurio extends BaseEgoCurio {
    private static final UUID HEALTH_UUID = UUID.fromString("9e594b47-35e3-4c58-b104-cf78f687a745");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("1170ed0d-f053-4494-a263-14f06f1ea943");
    static final UUID SET_HEALTH_UUID = UUID.fromString("06107abc-ee1f-4b47-a752-d8983bbb85c9");
    private static final String PANIC_HANDLED = "LobotocraftMustOpenPathPanicHandled";

    public MustOpenPathCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override public String curioName() { return MustOpenPathWeapon.SET_ID; }
    @Override public String projectionSlot() { return "headwear"; }
    @Override public boolean hasAnimatable() { return true; }
    @Override public boolean hasIdleAnimation() { return false; }
    @Override public boolean render2D() { return false; }
    @Override public boolean hasAttribute() { return true; }

    @Override
    public List<AttributeEntry> getAttributeEntries(@Nullable LivingEntity living) {
        return List.of(
                new AttributeEntry(HEALTH_UUID, "must_open_path_health",
                        Attributes.MAX_HEALTH, 10.0D, AttributeModifier.Operation.ADDITION),
                new AttributeEntry(ATTACK_SPEED_UUID, "must_open_path_attack_speed",
                        Attributes.ATTACK_SPEED, 0.20D, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (!(slotContext.entity() instanceof ServerPlayer player)) return;

        boolean complete = EgoArmorHelper.isFullEGOWithWeaponAnywhere(
                player, MustOpenPathWeapon.SET_ID);
        MustOpenPathMechanics.updateSetHealth(player, complete);
        if (!complete) {
            player.getPersistentData().putBoolean(PANIC_HANDLED, false);
            return;
        }

        if (!MentalValueUtil.isPanic(player)) {
            player.getPersistentData().putBoolean(PANIC_HANDLED, false);
            return;
        }
        if (player.getPersistentData().getBoolean(PANIC_HANDLED)) return;
        player.getPersistentData().putBoolean(PANIC_HANDLED, true);

        recoverFromPanic(player);
    }

    static boolean recoverFromPanic(ServerPlayer player) {
        float damage = player.getMaxHealth() * 0.65F;
        player.hurt(player.damageSources().magic(), damage);
        if (!player.isAlive()) return false;
        // Clear the server-owned panic type and runtime breakdown, not only the
        // legacy Isharmla marker.
        MentalValueEventHandler.forceRecover(player);
        MentalValueUtil.setMentalValue(player,
                MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.05F);
        for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
        EntityUtil.clearHurtTime(player);
        return !MentalValueUtil.isPanic(player);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        super.onUnequip(slotContext, newStack, stack);
        if (slotContext.entity() instanceof Player player) {
            MustOpenPathMechanics.updateSetHealth(player, false);
            player.getPersistentData().putBoolean(PANIC_HANDLED, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 10").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 20").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家生命值提高325点，受到伤害时，玩家屏幕将不再会抖动。")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家受到或者造成伤害时，会累计“必须维系的界限”；满蓄力攻击才会增加攻击方的进度。")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 必须维系的界限触发后，持续30秒，期间受到伤害或者造成伤害会刷新持续时间，并且只削减四分之一的无敌帧。")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家陷入恐慌后会受到自身生命值上限65%的伤害，解除恐慌并恢复5%精神值，同时清除负面状态。")
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
