package com.wzz.lobotocraft.item.ego.children_galaxy;

import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.base.IBodyPartRenderer.BodyPartType;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;

public class ChildrenGalaxyCurio extends BaseEgoCurio {
    private static final String SET_ID = "children_galaxy";
    private static final float BASE_RECOVERY = 3.0f;
    private static final double SHARE_RADIUS_SQR = 64.0D;

    public ChildrenGalaxyCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return SET_ID;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public BodyPartType getBodyPartType() {
        return BodyPartType.BODY;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 3;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.003f;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof ServerPlayer player && player.tickCount % 60 == 0) {
            triggerRecovery(player);
        }
    }

    public static boolean hasFullSet(Player player) {
        return EgoArmorHelper.isWearingFullSet(player, SET_ID)
                && EgoArmorHelper.isHoldingWeapon(player, SET_ID)
                && CuriosUtil.hasCurios(player, ModItems.CHILDREN_GALAXY_CURIO.get());
    }

    public static void triggerRecovery(ServerPlayer player) {
        if (player.isDeadOrDying()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean fullSet = hasFullSet(player);
        float amount = fullSet ? BASE_RECOVERY * 1.5f : BASE_RECOVERY;

        recover(player, amount);
        spawnRecoveryParticles(level, player);

        if (!fullSet) {
            return;
        }

        float sharedAmount = amount * 0.5f;
        for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
            if (other == player || other.serverLevel() != level || other.isDeadOrDying()) {
                continue;
            }
            if (other.distanceToSqr(player) > SHARE_RADIUS_SQR) {
                continue;
            }
            recover(other, sharedAmount);
            spawnRecoveryParticles(level, other);
        }
    }

    private static void recover(ServerPlayer player, float amount) {
        player.heal(amount);
        MentalValueUtil.addMentalValue(player, amount);
    }

    private static void spawnRecoveryParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.65D;
        double z = player.getZ();
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.35D, 0.45D, 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 4, 0.25D, 0.35D, 0.25D, 0.01D);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("  • 成功率 +3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 +3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 每3秒恢复3点生命值与精神值").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（武器 + 护甲 + 饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 小小银河的恢复效果提高50%").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 触发饰品恢复时，附近玩家恢复一半数值").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 持有友谊之证时，每次受伤立刻触发一次恢复").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
