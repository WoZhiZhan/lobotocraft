package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class MixinSlot {
    @Shadow public abstract ItemStack getItem();

    @Shadow @Final
    private int slot;

    @Shadow @Final
    public Container container;

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemstack = this.getItem();
        if (itemstack.getItem() instanceof BaseEgoArmor) {
            if (lobotocraft$isArmorSlot(this.container, this.slot)) {
                player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                    if (data.isArmorLocked()) {
                        cir.setReturnValue(false);
                    }
                });
            }
        }
    }

    @Unique
    private boolean lobotocraft$isArmorSlot(Container container, int slotIndex) {
        if (container instanceof Inventory) {
            // 玩家装备槽索引范围：头盔(39),胸甲(38),护腿(37),靴子(36)
            return slotIndex >= 36 && slotIndex <= 39;
        }
        return false;
    }
}
