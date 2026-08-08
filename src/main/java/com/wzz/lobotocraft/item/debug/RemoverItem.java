package com.wzz.lobotocraft.item.debug;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RemoverItem extends Item {
    public RemoverItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (RemovalTargetPolicy.isProtectedPlayerType(entity.getClass())) return true;
        if (!entity.level().isClientSide) entity.discard();
        return true;
    }

}
