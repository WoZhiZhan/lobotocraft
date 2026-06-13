package com.wzz.lobotocraft.item.ego.approval_birds;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ApprovalBirdCurio extends BaseEgoCurio {
    public ApprovalBirdCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "approval_bird";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 移动速度 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 压迫工作的成功率提高6%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备锁定 + 装备 + 饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用正义裁决者武器攻击，获得10%攻速和移速加成，持续10秒，不可叠加，每次攻击重置持续时间").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家进行压迫工作失误时，将不会受到伤害惩罚").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        if (workType == WorkType.REPRESSION)
            return 0.06f;
        return super.getWorkSuccessBonus(player, workType);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("d11af3db-9228-46e8-86b5-d8ee0d3cb609"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.012),
                new AttributeEntry(UUID.fromString("ad10faed-3990-4944-a653-11973c2ea441"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.024)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}