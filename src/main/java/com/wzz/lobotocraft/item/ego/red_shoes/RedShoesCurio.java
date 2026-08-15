package com.wzz.lobotocraft.item.ego.red_shoes;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RedShoesCurio extends BaseEgoCurio {
    public RedShoesCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "red_shoes";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.appendGiftDescription(
                tooltip,
                new String[]{"tooltip.lobotocraft.red_shoes_curio.effect.1"},
                ChatFormatting.GREEN,
                new String[]{
                        "tooltip.lobotocraft.red_shoes_curio.set.1",
                        "tooltip.lobotocraft.red_shoes_curio.set.2"
                },
                ChatFormatting.LIGHT_PURPLE
        );
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        entries.add(new AttributeEntry(UUID.fromString("67320f76-0d3b-4911-bd35-89c6e041c85d"),
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, 4.0D));
        return entries;
    }
}
