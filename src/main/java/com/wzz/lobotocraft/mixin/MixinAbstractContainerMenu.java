package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.api.IProhibitDiscarding;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.EgoProjectionRules;
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
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack clicked = ItemStack.EMPTY;
        if (slotId >= 0 && slotId < player.containerMenu.slots.size()) {
            clicked = player.containerMenu.slots.get(slotId).getItem();
        }
        ItemStack swapped = ItemStack.EMPTY;
        if (clickType == ClickType.SWAP) {
            if (dragType >= 0 && dragType < 9) swapped = player.getInventory().getItem(dragType);
            else if (dragType == 40) swapped = player.getOffhandItem();
        }
        boolean projectionAction = EgoLoadoutService.isProjection(carried)
                || EgoLoadoutService.isProjection(clicked)
                || EgoLoadoutService.isProjection(swapped);
        if (projectionAction) {
            boolean inventoryOnlyMenu = player.containerMenu == player.inventoryMenu;
            boolean supportedMove = clickType == ClickType.PICKUP || clickType == ClickType.SWAP
                    || inventoryOnlyMenu && (clickType == ClickType.QUICK_MOVE
                    || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL);
            boolean ownInventorySlot = slotId >= 0 && slotId < player.containerMenu.slots.size()
                    && player.containerMenu.slots.get(slotId).container == player.getInventory()
                    || inventoryOnlyMenu && clickType == ClickType.QUICK_CRAFT && slotId == -999;
            boolean clickedOwnerMatches = !EgoLoadoutService.isProjection(clicked)
                    || EgoLoadoutService.isProjectionOwnedBy(clicked, player.getUUID());
            boolean carriedOwnerMatches = !EgoLoadoutService.isProjection(carried)
                    || EgoLoadoutService.isProjectionOwnedBy(carried, player.getUUID());
            boolean swappedOwnerMatches = !EgoLoadoutService.isProjection(swapped)
                    || EgoLoadoutService.isProjectionOwnedBy(swapped, player.getUUID());
            if (!EgoProjectionRules.mayClickMove(supportedMove, ownInventorySlot,
                    clickedOwnerMatches && swappedOwnerMatches,
                    carriedOwnerMatches, player.isCreative())) {
                ci.cancel();
                return;
            }
        }
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
