package com.wzz.lobotocraft.item.debug;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class StopEscapeItem extends Item {
    public StopEscapeItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof AbstractAbnormality abnormality && abnormality.hasEscape()) {
            abnormality.stopEscape();
            if (player.level.isClientSide)
                player.displayClientMessage(Component.literal(abnormality.getAbnormalityName() + " 已停止出逃"), false);
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }
}
