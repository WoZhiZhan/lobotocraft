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
	public static final RegistryObject<ParticleType<?>> ARMY_IN_BLACK_EXPLODE = REGISTRY.register("army_in_black_explode", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> ARMY_IN_BLACK_HALF_SHOOT = REGISTRY.register("army_in_black_half_shoot", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> ARMY_IN_BLACK_NORMAL_SHOOT = REGISTRY.register("army_in_black_normal_shoot", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> ARMY_IN_BLACK_SPUTTERING = REGISTRY.register("army_in_black_sputtering", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> SMILING_CORPSE_MOUNTAIN_VOMITUS_1 = REGISTRY.register("smiling_corpse_mountain_vomitus_1", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> SMILING_CORPSE_MOUNTAIN_VOMITUS_2 = REGISTRY.register("smiling_corpse_mountain_vomitus_2", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> SMILING_CORPSE_MOUNTAIN_VOMITUS_3 = REGISTRY.register("smiling_corpse_mountain_vomitus_3", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> SMILING_CORPSE_MOUNTAIN_VOMITUS_4 = REGISTRY.register("smiling_corpse_mountain_vomitus_4", () -> new SimpleParticleType(true));
	public static final RegistryObject<ParticleType<?>> SMILING_CORPSE_MOUNTAIN_VOMITUS_5 = REGISTRY.register("smiling_corpse_mountain_vomitus_5", () -> new SimpleParticleType(true));
}
