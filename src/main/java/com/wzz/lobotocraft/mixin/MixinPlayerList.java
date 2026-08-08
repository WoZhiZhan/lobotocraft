package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Inject(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.BEFORE)
    )
    private void lobotocraft$sendEmployeeProfilesBeforeTracking(Connection connection,
                                                                ServerPlayer player,
                                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        EmployeeRuntimeManager.sendPlayerInfo(player);
    }

    @Inject(method = "getPlayerByName", at = @At("RETURN"), cancellable = true)
    private void lobotocraft$findEmployeeByName(String name,
                                                CallbackInfoReturnable<ServerPlayer> callback) {
        if (callback.getReturnValue() == null) {
            callback.setReturnValue(EmployeeRuntimeManager.activePlayerByName(name));
        }
    }

    @Inject(method = "getPlayer", at = @At("RETURN"), cancellable = true)
    private void lobotocraft$findEmployeeByUuid(UUID id,
                                                CallbackInfoReturnable<ServerPlayer> callback) {
        if (callback.getReturnValue() == null) {
            callback.setReturnValue(EmployeeRuntimeManager.activePlayer(id));
        }
    }
}
