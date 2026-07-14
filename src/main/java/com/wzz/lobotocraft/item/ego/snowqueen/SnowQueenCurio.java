package com.wzz.lobotocraft.item.ego.snowqueen;

import com.wzz.lobotocraft.init.ModAttributes;
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

public class SnowQueenCurio extends BaseEgoCurio {
    public SnowQueenCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "snowqueen";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大精神值 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家每次使用“霜之碎片”造成伤害时，有10%的概率施加一层亲吻BUFF，持续10秒。每层亲吻减少生物40%移动速度，当达到三层时会冰封生物6秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 被冰封的生物受到红色伤害x2，受到红色伤害时立刻解除冰封并受到自身生命值上限2%的红色伤害").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家不会受到火焰伤害").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("玫瑰盛开...雪宫崩塌...").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("欢笑的人们不曾记得...").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("在那有位沉睡的美人...").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("28a5b459-0e25-4d7d-92b6-110835a9e854"),
                        this.getCurrentClassName() + " Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 6D),
                new AttributeEntry(UUID.fromString("758ba69a-d290-4c3d-b4f7-ed65ef5ec2f5"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 6D)
        );
    }
}
