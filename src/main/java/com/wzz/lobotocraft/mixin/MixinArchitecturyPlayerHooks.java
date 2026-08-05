package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LobotoCraft employees are ServerPlayer-shaped entities so the existing player
 * equipment and capability mechanics apply to them. They are not logged-in users,
 * however, and therefore have no third-party account or team data. Expose that fact
 * through Architectury's standard fake-player hook without inheriting Forge's
 * invulnerable, non-combat FakePlayer implementation.
 */
@Pseudo
@Mixin(targets = "dev.architectury.hooks.level.entity.forge.PlayerHooksImpl", remap = false)
public abstract class MixinArchitecturyPlayerHooks {
    @Inject(
            method = "isFake(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void lobotocraft$identifyAiEmployee(Player player,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof EmployeeServerPlayer) {
            callback.setReturnValue(true);
        }
    }
}
