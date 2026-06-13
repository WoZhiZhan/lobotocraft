package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModMain.MODID);

	public static final RegistryObject<CreativeModeTab> TAB_LOBOTOCRAFT_ENTITY = REGISTRY.register("tablobotocraft_entity",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup.lobotocraft_entity")).icon(() -> new ItemStack(ModItems.ONEBAD_SPAWN_EGG.get())).displayItems((parameters, tabData) -> {
						tabData.accept(ModItems.ONEBAD_SPAWN_EGG.get());
						tabData.accept(ModItems.HAPPY_TEDDY_SPAWN_EGG.get());
						tabData.accept(ModItems.IRON_MAIDEN_SPAWN_EGG.get());
						tabData.accept(ModItems.MEAT_IDOL_SPAWN_EGG.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_SPAWN_EGG.get());
						tabData.accept(ModItems.LARGE_BIRD_SPAWN_EGG.get());
						tabData.accept(ModItems.PUNISHING_BIRD_SPAWN_EGG.get());
						tabData.accept(ModItems.APPROVAL_BIRD_SPAWN_EGG.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_SPAWN_EGG.get());
						tabData.accept(ModItems.WINGBEAT_SPAWN_EGG.get());
						tabData.accept(ModItems.SNOWQUEEN_SPAWN_EGG.get());
						tabData.accept(ModItems.PPODAE_SPAWN_EGG.get());
						tabData.accept(ModItems.CHILDREN_GALAXY_SPAWN_EGG.get());
						tabData.accept(ModItems.BLUE_STAR_SPAWN_EGG.get());
						tabData.accept(ModItems.THORN_BUS_SPAWN_EGG.get());
						tabData.accept(ModItems.END_BIRD_SPAWN_EGG.get());
						tabData.accept(ModItems.ISHARMLA_SPAWN_EGG.get());
						tabData.accept(ModItems.BUTTERFLY_FUNERAL_SPAWN_EGG.get());
						tabData.accept(ModItems.REDHAT_MERCENARY_SPAWN_EGG.get());
						tabData.accept(ModItems.FRAGMENT_OF_THE_UNIVERSE_SPAWN_EGG.get());
						tabData.accept(ModItems.BIGBADWOLF_SPAWN_EGG.get());
						tabData.accept(ModItems.HELPER_SPAWN_EGG.get());
						tabData.accept(ModItems.SKADI_SPAWN_EGG.get());
						tabData.accept(ModItems.CLEANER_SPAWN_EGG.get());
						tabData.accept(ModItems.SHELL_SEA_RUNNER_SPAWN_EGG.get());
						tabData.accept(ModItems.DEEPSEA_SLIDER_SPAWN_EGG.get());
						tabData.accept(ModItems.RIDGESEA_SPITTER_SPAWN_EGG.get());
						tabData.accept(ModItems.PRIMALSEA_PIERCER_SPAWN_EGG.get());
						tabData.accept(ModItems.NUCLEIC_MALEFICENT_SPAWN_EGG.get());
						tabData.accept(ModItems.BASINSEA_REAPER_SPAWN_EGG.get());
					})
					.build());

	public static final RegistryObject<CreativeModeTab> TAB_LOBOTOCRAFT_CORE = REGISTRY.register("tablobotocraft_core",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup.lobotocraft_core")).icon(() -> new ItemStack(ModItems.ICON.get())).displayItems((parameters, tabData) -> {
						tabData.accept(ModItems.PE_BOX.get());
						tabData.accept(ModItems.CONVEYER.get());
						tabData.accept(ModItems.REGENERATION_REACTOR.get());
						// 便携式抑制器已改为左上角 HUD 计数机制,不再以物品形式提供
						tabData.accept(ModItems.ARMOR_LOCK.get());
						tabData.accept(ModItems.SPECIAL_RECORD.get());
						tabData.accept(ModItems.TARGET_MARKER.get());
						tabData.accept(ModItems.SHEEPSKIN_CURIO.get());
						tabData.accept(ModItems.STRANGE_BADGE.get());
						tabData.accept(ModItems.OTTO.get());
						tabData.accept(ModItems.ESCAPE.get());
						tabData.accept(ModItems.ELEVATOR.get());
						tabData.accept(ModItems.PUNISHING_BIRD_BLOCK.get());
						tabData.accept(ModItems.TT2.get());
						tabData.accept(ModItems.CAPTURE_UNIT.get());
					})
					.build());

	public static final RegistryObject<CreativeModeTab> TAB_LOBOTOCRAFT_SUIT = REGISTRY.register("tablobotocraft_suit",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup.lobotocraft_suit")).icon(() -> new ItemStack(ModItems.ICON.get())).displayItems((parameters, tabData) -> {
						tabData.accept(ModItems.REPENTANCE_CHESTPLATE.get());
						tabData.accept(ModItems.REPENTANCE_LEGGINGS.get());
						tabData.accept(ModItems.REPENTANCE_BOOTS.get());
						tabData.accept(ModItems.REPENTANCE_WEAPON.get());
						tabData.accept(ModItems.REPENTANCE_CURIO.get());
						tabData.accept(ModItems.BLUE_STAR_CHESTPLATE.get());
						tabData.accept(ModItems.BLUE_STAR_LEGGINGS.get());
						tabData.accept(ModItems.BLUE_STAR_BOOTS.get());
						tabData.accept(ModItems.BLUE_STAR_WEAPON.get());
						tabData.accept(ModItems.BLUE_STAR_CURIO.get());
						tabData.accept(ModItems.LARGEBIRD_CHESTPLATE.get());
						tabData.accept(ModItems.LARGEBIRD_LEGGINGS.get());
						tabData.accept(ModItems.LARGEBIRD_BOOTS.get());
						tabData.accept(ModItems.LARGEBIRD_CURIO.get());
						tabData.accept(ModItems.LARGEBIRD_WEAPON.get());
						tabData.accept(ModItems.PUNISHING_BIRD_CHESTPLATE.get());
						tabData.accept(ModItems.PUNISHING_BIRD_LEGGINGS.get());
						tabData.accept(ModItems.PUNISHING_BIRD_BOOTS.get());
						tabData.accept(ModItems.PUNISHING_BIRD_WEAPON.get());
						tabData.accept(ModItems.PUNISHING_BIRD_CURIO.get());
						tabData.accept(ModItems.APPROVAL_BIRD_CHESTPLATE.get());
						tabData.accept(ModItems.APPROVAL_BIRD_LEGGINGS.get());
						tabData.accept(ModItems.APPROVAL_BIRD_BOOTS.get());
						tabData.accept(ModItems.APPROVAL_BIRD_WEAPON.get());
						tabData.accept(ModItems.APPROVAL_BIRD_CURIO.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_CHESTPLATE.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_LEGGINGS.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_BOOTS.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_WEAPON.get());
						tabData.accept(ModItems.FOURTH_MATCH_FLAME_CURIO.get());
						tabData.accept(ModItems.WINGBEAT_CHESTPLATE.get());
						tabData.accept(ModItems.WINGBEAT_LEGGINGS.get());
						tabData.accept(ModItems.WINGBEAT_BOOTS.get());
						tabData.accept(ModItems.WINGBEAT_WEAPON.get());
						tabData.accept(ModItems.WINGBEAT_CURIO.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_CHESTPLATE.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_LEGGINGS.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_BOOTS.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_WEAPON.get());
						tabData.accept(ModItems.ABANDONED_MURDERER_CURIO.get());
						tabData.accept(ModItems.THORN_BUS_CHESTPLATE.get());
						tabData.accept(ModItems.THORN_BUS_LEGGINGS.get());
						tabData.accept(ModItems.THORN_BUS_BOOTS.get());
						tabData.accept(ModItems.THORN_BUS_WEAPON.get());
						tabData.accept(ModItems.THORN_BUS_CURIO.get());
						tabData.accept(ModItems.END_BIRD_CHESTPLATE.get());
						tabData.accept(ModItems.END_BIRD_LEGGINGS.get());
						tabData.accept(ModItems.END_BIRD_BOOTS.get());
						tabData.accept(ModItems.END_BIRD_WEAPON.get());
						tabData.accept(ModItems.END_BIRD_CURIO.get());
					})
					.build());

	public static final RegistryObject<CreativeModeTab> TAB_LOBOTOCRAFT_DEBUG = REGISTRY.register("tablobotocraft_debug",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup.lobotocraft_debug")).icon(() -> new ItemStack(ModItems.STRUCTURE_TOOL_SAFE.get())).displayItems((parameters, tabData) -> {
						tabData.accept(ModItems.STRUCTURE_TOOL_SAFE.get());
						tabData.accept(ModItems.REMOVER.get());
						tabData.accept(ModItems.ENTITY_ESCAPE.get());
						tabData.accept(ModItems.STOP_ESCAPE.get());
						tabData.accept(ModItems.ICON.get());
					})
					.build());
}
