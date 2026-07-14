package com.wzz.lobotocraft.item.ego.bigbadwolf;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class BigBadwolfCurio extends BaseEgoCurio {
    public BigBadwolfCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "big_badwolf";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 移动速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 玩家使用任意EGO武器时造成的红色伤害 + 10%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用“郁蓝创痕”造成伤害时，10tick内减少目标60%移速。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家每受到等同于自身生命值上限25%的伤害时，进入“潜狼状态”，持续5秒，5秒内每秒只会受到一次伤害。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("d39d69c6-1fd2-44ad-bd7d-134997ba64ae"),
                        this.getCurrentClassName() + "Max Health Bonus", Attributes.MOVEMENT_SPEED, 4D),
                new AttributeEntry(UUID.fromString("b4d8d3d7-8061-4b4c-bce4-f7ea74d2e7b4"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.024D),
                new AttributeEntry(UUID.fromString("18423f10-2d24-4142-b8ef-5997293af7ed"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.012D)
        );
    }
}
