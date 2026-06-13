package com.wzz.lobotocraft.item.block;

import com.wzz.lobotocraft.init.ModBlocks;
import net.minecraft.world.item.BlockItem;

public class EscapeBlockItem extends BlockItem {
    public EscapeBlockItem() {
        super(ModBlocks.ESCAPE.get(), new Properties().stacksTo(64).fireResistant());
    }
}