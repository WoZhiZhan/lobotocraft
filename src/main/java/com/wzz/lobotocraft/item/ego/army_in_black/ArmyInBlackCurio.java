package com.wzz.lobotocraft.item.ego.army_in_black;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ArmyInBlackCurio extends BaseEgoCurio {
    public ArmyInBlackCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "army_in_black";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大精神值 + 5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 装备全套“粉红军备”时，E.G.O武器“粉红军备”的攻击力将提高15点").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家进行任意工作时，成功率提高10%。工作失误造成的伤害更改为1点。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家使用武器“粉红军备”命中生物后，恢复10点生命值与精神值。若生命值低于50%则改为每次溅射都会恢复3点精神值与生命值。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 当玩家精神值全满时，单次受伤不会超过生命值30%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        if (EgoArmorHelper.isFullEGO(player, "army_in_black")) {
            return 0.1f;
        }
        return super.getWorkSuccessBonus(player, workType);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("1ad9aaaf-e46b-4aad-b3d2-c33ffa470b4d"),
                        this.getCurrentClassName() + "Max Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 5D),
                new AttributeEntry(UUID.fromString("6ba56a33-7401-47fe-b889-315f609d00b8"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.025D, Mode.MULTIPLY_TOTAL)
        );
    }
}
