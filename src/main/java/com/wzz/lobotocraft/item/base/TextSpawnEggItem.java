package com.wzz.lobotocraft.item.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class TextSpawnEggItem extends ForgeSpawnEggItem {
    private final String[] texts;

    public TextSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                            int backgroundColor, int highlightColor, Properties props,
                            String text) {
        this(type, backgroundColor, highlightColor, props, new String[]{text});
    }

    public TextSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                            int backgroundColor, int highlightColor, Properties props,
                            String... texts) {
        super(type, backgroundColor, highlightColor, props);
        this.texts = texts;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        for (String text : texts) {
            tooltip.add(Component.literal(text));
        }
    }
}