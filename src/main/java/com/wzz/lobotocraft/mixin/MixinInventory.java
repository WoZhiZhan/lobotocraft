package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.api.IProhibitDiscarding;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(Inventory.class)
public abstract class MixinInventory {
    @Shadow public abstract ItemStack getItem(int p_35991_);

    @Shadow @Final private List<NonNullList<ItemStack>> compartments;

    @Shadow @Final public Player player;

    @ModifyVariable(
            method = "clearOrCountMatchingItems",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Predicate<ItemStack> modifyPredicate(Predicate<ItemStack> original) {
        return stack -> {
            if (stack.getItem() instanceof IProhibitDiscarding && !player.isCreative()) {
                return false;
            }
            return original.test(stack);
        };
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void setItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (!this.player.isCreative()) {
            ItemStack currentStack = this.getItem(slot);
            if (currentStack.getItem() instanceof IProhibitDiscarding) {
                if (!stack.isEmpty() && !(stack.getItem() instanceof IProhibitDiscarding)) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void dropAll(CallbackInfo ci) {
        if (!this.player.isCreative()) {
            for (List<ItemStack> list : this.compartments) {
                for (int i = 0; i < list.size(); ++i) {
                    ItemStack itemstack = list.get(i);
                    if (!itemstack.isEmpty() && !(itemstack.getItem() instanceof IProhibitDiscarding)) {
                        this.player.drop(itemstack, true, false);
                        list.set(i, ItemStack.EMPTY);
                    }
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true)
    private void clearContent(CallbackInfo ci) {
        if (!this.player.isCreative()) {
            for (List<ItemStack> list : this.compartments) {
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = list.get(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof IProhibitDiscarding) {
                        continue;
                    }
                    list.set(i, ItemStack.EMPTY);
                }
            }
            ci.cancel();
        }
    }
}
