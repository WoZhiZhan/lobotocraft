package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.ego.nothing_there.NothingThereCurio;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
public class MixinPlayerFoodExhaustion {

    @ModifyArg(
            method = "causeFoodExhaustion",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V")
    )
    private float lobotocraft$nothingThereSprintReduction(float amount) {
        Player self = (Player) (Object) this;
        if (self.isSprinting() && NothingThereCurio.sprintExhaustionReduced(self)) {
            return amount * 0.2F; // -80%
        }
        return amount;
    }
}