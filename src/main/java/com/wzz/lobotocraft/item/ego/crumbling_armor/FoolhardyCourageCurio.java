package com.wzz.lobotocraft.item.ego.crumbling_armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FoolhardyCourageCurio extends BaseEgoCurio {
    private static final UUID HEALTH_PENALTY_UUID = UUID.fromString("c0610003-0561-4000-8000-000000000003");
    private static final UUID MOVE_SPEED_UUID = UUID.fromString("c0610004-0561-4000-8000-000000000004");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("c0610005-0561-4000-8000-000000000005");

    public FoolhardyCourageCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "crumbling_armor";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public boolean rendersOnWearer() {
        return false;
    }

    @Override
    public BodyPartType getBodyPartType() {
        return BodyPartType.BODY;
    }

    @Override
    public void applyTransform(PoseStack poseStack, HumanoidModel<?> model, BodyPartType part) {
        model.body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.15F, 0.42F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
    }

    /**
     * 套装（装备+武器+饰品）是否生效。
     * 只有套装生效时才免除“匹夫之勇”的 -20 最大生命值惩罚。
     *
     * 如果希望必须【手持决死之心】才免除惩罚，把下面改成：
     *   return EgoArmorHelper.isFullEGO(player, "crumbling_armor")
     *           && EgoArmorHelper.isHoldingWeapon(player, "crumbling_armor");
     * 如果希望套装也照样扣血（永远 -20），直接 return false; 即可。
     */
    private static boolean isFullSetActive(LivingEntity living) {
        return living instanceof Player player
                && EgoArmorHelper.isFullEGO(player, "crumbling_armor");
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        boolean hasFullSuit = isFullSetActive(living);

        entries.add(new AttributeEntry(HEALTH_PENALTY_UUID,
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, hasFullSuit ? 0.0D : -20.0D));

        // 移动速度：基础20%，套装额外5%
        double speedBonus = hasFullSuit ? 0.25D : 0.20D;
        entries.add(new AttributeEntry(MOVE_SPEED_UUID,
                this.getCurrentClassName() + " Move Speed Bonus",
                Attributes.MOVEMENT_SPEED, speedBonus,
                AttributeModifier.Operation.MULTIPLY_BASE));

        // 攻击速度：基础20%，套装额外5%
        double attackSpeedBonus = hasFullSuit ? 0.25D : 0.20D;
        entries.add(new AttributeEntry(ATTACK_SPEED_UUID,
                this.getCurrentClassName() + " Attack Speed Bonus",
                Attributes.ATTACK_SPEED, attackSpeedBonus,
                AttributeModifier.Operation.MULTIPLY_BASE));

        return entries;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof Player player) {
            if (!player.level().isClientSide) {
                // 上限降低时把当前血量夹回上限，避免出现“血量 > 上限”的显示
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
            if (player.tickCount % 60 == 0) {
                ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.RED_LIGHT.get(), 20, 0.1D);
            }
        }
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 移动速度 + 20").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 20").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大生命值 -20").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("由内在的勇气膨胀而成的匹夫之勇。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("匹夫之勇，终将葬送一切。").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal("携带者进行沟通工作时会被处决。").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 当8x8范围内没有其他玩家时，手持武器“决死之心”的玩家造成的伤害提高35%，受到的伤害减少10%").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家不再受到匹夫之勇减少生命值的影响，并且额外提升5%的移动速度和攻击速度").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}