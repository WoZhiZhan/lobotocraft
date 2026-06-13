package com.wzz.lobotocraft.item.api;

import com.wzz.lobotocraft.entity.EntityImmortalItem;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public abstract class ProhibitDiscardingItem extends Item implements IProhibitDiscarding {
    public ProhibitDiscardingItem(Properties p_41383_) {
        super(p_41383_);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public @Nullable Entity createEntity(Level level, Entity location, ItemStack stack) {
        return EntityImmortalItem.create(ModEntities.immortal_item.get(), level, location.getX(), location.getY(), location.getZ(), stack);
    }
}
