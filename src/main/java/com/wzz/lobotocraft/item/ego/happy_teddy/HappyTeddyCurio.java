package com.wzz.lobotocraft.item.ego.happy_teddy;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HappyTeddyCurio extends BaseEgoCurio {
    public HappyTeddyCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "happy_teddy";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大精神值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 沟通工作的成功率 + 3%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家进行沟通工作时，成功率额外提高6%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家使用武器“熊熊抱”造成伤害时，有20%概率额外造成一次等同于这次伤害的白色伤害。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家使用武器“熊熊抱”每造成40红色伤害，则眩晕对方1秒。期间无法移动和攻击。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public Attribute getAttribute() {
        return ModAttributes.EXTRA_MENTAL_VALUE.get();
    }
    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public UUID getAttributeUUID() {
        return UUID.fromString("7801d735-4491-4d78-9b2d-76db294b708e");
    }

    @Override
    public float getAttributeBonus() {
        return 4f;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        float bonus = 0.03f;
        if (workType == WorkType.ATTACHMENT) {
            if (EgoArmorHelper.isFullEGO(player, "happy_teddy"))
                bonus += 0.06f;
            return bonus;
        }
        return super.getWorkSuccessBonus(player, workType);
    }
}
