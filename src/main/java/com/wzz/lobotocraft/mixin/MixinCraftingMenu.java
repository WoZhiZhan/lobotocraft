package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.CaptureUnitItem;
import com.wzz.lobotocraft.util.SuppressorCounterUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingMenu.class)
public abstract class MixinCraftingMenu {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void gateCaptureUnitQuickCraft(Player player, int slotIndex,
                                           CallbackInfoReturnable<ItemStack> cir) {
        if (player.level().isClientSide || slotIndex != 0) return;

        CraftingMenu menu = (CraftingMenu) (Object) this;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.getItem() instanceof CaptureUnitItem
                && SuppressorCounterUtil.get(player) < SuppressorCounterUtil.capacity(player)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
