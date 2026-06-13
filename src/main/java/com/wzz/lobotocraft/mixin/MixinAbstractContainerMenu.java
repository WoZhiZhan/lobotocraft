package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.api.IProhibitDiscarding;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onClicked(int slotId, int dragType, ClickType clickType, Player player, CallbackInfo ci) {
        if (clickType != ClickType.THROW) return;
        if (player.isCreative()) return;
        ItemStack target = ItemStack.EMPTY;
        if (slotId >= 0 && slotId < player.containerMenu.slots.size()) {
            target = player.containerMenu.slots.get(slotId).getItem();
        }
        if (slotId == -999) {
            target = player.containerMenu.getCarried();
        }
        if (!target.isEmpty() && target.getItem() instanceof IProhibitDiscarding) {
            ci.cancel();
        }
    }
}
