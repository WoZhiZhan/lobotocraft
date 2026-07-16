package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.client.particle.AttackParticle;
import com.wzz.lobotocraft.client.particle.BlueGlintParticle;
import com.wzz.lobotocraft.client.particle.ButterflyParticle;
import com.wzz.lobotocraft.client.particle.ColorLightParticle;
import com.wzz.lobotocraft.color.ExtendedColor;
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
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.GOLD_LIGHT.get(), ColorLightParticle.Provider::new);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.DARK_BLUE_LIGHT.get(),
				(spriteSet -> ColorLightParticle.createProvider(spriteSet,  ExtendedColor.MATERIAL_BLUE, ExtendedColor.BLUE)));
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.ARMY_IN_BLACK_EXPLODE.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.ARMY_IN_BLACK_HALF_SHOOT.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.ARMY_IN_BLACK_NORMAL_SHOOT.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.ARMY_IN_BLACK_SPUTTERING.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_1.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_2.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_3.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_4.get(), AttackParticle::provider);
		event.registerSpriteSet((SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_5.get(), AttackParticle::provider);
	}
}