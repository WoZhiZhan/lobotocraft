package com.lobotocraft.facility.mixin;

import com.lobotocraft.facility.damage.DamageHistory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {
    @Redirect(
        method = "die",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;")
    )
    private Component facility$addDamageHistory(CombatTracker tracker) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return DamageHistory.decorateDeathMessage(player, tracker.getDeathMessage());
    }
}
