package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.client.particle.AttackParticle;
import com.wzz.lobotocraft.client.particle.BlueGlintParticle;
import com.wzz.lobotocraft.client.particle.ButterflyParticle;
import com.wzz.lobotocraft.client.particle.ColorLightParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModParticles {
	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.RED.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.WHITE.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.BLUE.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.BLACK.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.BLUE_GLINT.get(), BlueGlintParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.BUTTERFLY.get(), ButterflyParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.RED_LIGHT.get(),
				(spriteSet -> new ColorLightParticle.Provider(spriteSet, 1.0F, 0.1F, 0.1F, 0.6F, 0.0F, 0.0F)));
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.BLUE_LIGHT.get(),
				(spriteSet -> new ColorLightParticle.Provider(spriteSet, 0.2F, 0.3F, 1.0F, 0.0F, 0.0F, 0.7F)));
	}
}