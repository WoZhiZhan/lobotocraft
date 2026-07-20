package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
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
    private float lobotocraft$sprintExhaustionReduction(float amount) {
        Player self = (Player) (Object) this;
        float result = amount;
        if (self.isSprinting() && NothingThereCurio.sprintExhaustionReduced(self)) {
            result *= 0.2F;
        }
        if (self.isSprinting() && CoreSuppressionManager.hasReward(self, CoreSuppressionType.MALKUTH)) {
            result *= 0.8F;
        }
        return result;
    }
}
