package com.wzz.lobotocraft.item.ego;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SheepskinCurio extends BaseEgoCurio {
    public SheepskinCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "sheepskin";
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
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.sheepskin_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.sheepskin_curio.set_header",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.sheepskin_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.sheepskin_curio.story",
                ChatFormatting.GRAY);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(@Nullable LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("69170e61-980d-4a9b-aad3-f6da9f0218d1"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 9D),
                new AttributeEntry(UUID.fromString("c650ea2a-f7d3-429f-865a-731bd18cee10"),
                        this.getCurrentClassName() + " Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 5D)
        );
    }
}
