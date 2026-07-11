package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.event.living.LivingSwingEvent;
import com.wzz.lobotocraft.init.ModEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void onKnockback(double p_147241_, double p_147242_, double p_147243_, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player)
            ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void aiStep(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getPersistentData().getBoolean("NotMove"))
            ci.cancel();
        if (!(self instanceof Player)) {
            MobEffectInstance kissEffect = self.getEffect(ModEffects.KISS.get());
            if (kissEffect != null && kissEffect.getAmplifier() >= 2) {
                self.attackAnim = 0;
                self.walkAnimation.setSpeed(0);
                self.walkAnimation.position(self.walkAnimation.position());
                ci.cancel();
            }
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void travel(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getPersistentData().getBoolean("NotMove"))
            ci.cancel();
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingSwingEvent.Pre event = new LivingSwingEvent.Pre(self);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At("RETURN"))
    private void onSwingAfter(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingSwingEvent.Post event = new LivingSwingEvent.Post(self);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }
}