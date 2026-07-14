package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticleTypes {
	public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModMain.MODID);
	public static final RegistryObject<ParticleType<?>> RED = REGISTRY.register("red", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> WHITE = REGISTRY.register("white", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> BLUE = REGISTRY.register("blue", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> BLACK = REGISTRY.register("black", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> BLUE_GLINT = REGISTRY.register("blue_glint", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> BUTTERFLY = REGISTRY.register("butterfly", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> RED_LIGHT = REGISTRY.register("red_light", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> BLUE_LIGHT = REGISTRY.register("blue_light", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> GOLD_LIGHT = REGISTRY.register("gold_light", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> DARK_BLUE_LIGHT = REGISTRY.register("dark_blue_light", () -> new SimpleParticleType(true));
}
