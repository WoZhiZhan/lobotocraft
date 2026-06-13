package com.wzz.lobotocraft.item.debug;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EscapeItem extends Item {
    public EscapeItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof AbstractAbnormality abnormality) {
            abnormality.triggerEscape();
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }
}
