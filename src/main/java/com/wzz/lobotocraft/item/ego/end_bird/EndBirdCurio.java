package com.wzz.lobotocraft.item.ego.end_bird;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.ClientInputUtil;
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

public class EndBirdCurio extends BaseEgoCurio {
    public EndBirdCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "end_bird";
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public boolean hasMoveAnimation() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (ClientInputUtil.isShiftPressed()) {
            tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("  • 最大生命值 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 最大精神值 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 工作成功率 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 工作速度 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 移动速度 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 攻击速度 + 7").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("  • 造成伤害提高 10%").withStyle(ChatFormatting.GREEN));
            return;
        }
        tooltip.add(Component.literal("人们最终战胜了黄昏的黑暗，准备面对黎明的光辉。").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("而在那片昏暗的森林中，鸟儿的叽喳鸣唱依旧响彻着吗？").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("§7按住<Shift>查看详情"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 拥有惩戒鸟饰品时，“普通攻击”造成伤害将回复等同于伤害值10%的血量。被薄暝装备被动命中的目标添加一个15秒的全属性易伤。使其受到来自“特殊攻击”的伤害提高25%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 拥有大鸟饰品时，将不会再受到魅惑效果。玩家陷入恐慌状态将立刻解除。使用特殊攻击时，生成一个吸收全属性伤害的护盾（玩家生命值50%），持续15秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 拥有审判鸟饰品时。玩家受到的单次伤害将不超过生命值上限20%。攻击目标为自己提高30%的攻速和移速。持续10秒不可叠加，每次攻击刷新持续时间。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 14;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.07F;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("ec348c7c-f0df-425c-b6cf-c3e4a9247168"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 7),
                new AttributeEntry(UUID.fromString("a9e9019f-754c-4878-b3a9-5c500eee001c"),
                        this.getCurrentClassName() + " Max Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 7),
                new AttributeEntry(UUID.fromString("5c105315-364b-4bd5-8ccb-861ac1c9d984"),
                        this.getCurrentClassName() + " Max Speed Bonus", Attributes.MOVEMENT_SPEED, 0.014D),
                new AttributeEntry(UUID.fromString("e29489fd-d169-474a-8f16-d8e7ee3015a8"),
                        this.getCurrentClassName() + " Max Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.026D)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}