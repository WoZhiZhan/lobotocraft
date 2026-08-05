package com.lobotocraft.facility.mixin.client;

import com.lobotocraft.facility.client.PlayerStatusClientState;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void facility$appendStatus(PlayerInfo playerInfo,
                                       CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(callback.getReturnValue().copy()
            .append(PlayerStatusClientState.display(playerInfo.getProfile().getId())));
    }
}
