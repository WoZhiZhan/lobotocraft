package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.*;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCommonEvent {
	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(ModEntities.onebad.get(), EntityOneBad.createAttributes().build());
		event.put(ModEntities.clerk.get(), EntityClerk.createAttributes().build());
		event.put(ModEntities.happy_teddy.get(), EntityHappyTeddy.createAttributes().build());
		event.put(ModEntities.skadi_corrupted.get(), EntityDarkSkadi.createAttributes().build());
		event.put(ModEntities.isharmla.get(), EntityIsharmla.createAttributes().build());
		event.put(ModEntities.isharmla_tear.get(), EntityIsharmlaTear.createAttributes().build());
		event.put(ModEntities.shell_sea_runner.get(), com.wzz.lobotocraft.entity.seaborn.EntityShellSeaRunner.createAttributes().build());
		event.put(ModEntities.deepsea_slider.get(), com.wzz.lobotocraft.entity.seaborn.EntityDeepSeaSlider.createAttributes().build());
		event.put(ModEntities.ridgesea_spitter.get(), com.wzz.lobotocraft.entity.seaborn.EntityRidgeSeaSpitter.createAttributes().build());
		event.put(ModEntities.primalsea_piercer.get(), com.wzz.lobotocraft.entity.seaborn.EntityPrimalSeaPiercer.createAttributes().build());
		event.put(ModEntities.nucleic_maleficent.get(), com.wzz.lobotocraft.entity.seaborn.EntityNucleicMaleficent.createAttributes().build());
		event.put(ModEntities.basinsea_reaper.get(), com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaReaper.createAttributes().build());
		event.put(ModEntities.cleaner.get(), EntityCleaner.createAttributes().build());
		event.put(ModEntities.butterfly_funeral.get(), EntityButterflyFuneral.createAttributes().build());
		event.put(ModEntities.fragment_of_the_universe.get(), EntityFragmentOfUniverse.createAttributes().build());
		event.put(ModEntities.bigbadwolf.get(), EntityBigBadWolf.createAttributes().build());
		event.put(ModEntities.helper.get(), EntityHelper.createAttributes().build());
		event.put(ModEntities.redhat_mercenary.get(), EntityRedHoodMercenary.createAttributes().build());
		event.put(ModEntities.iron_maiden.get(), EntityIronMaiden.createAttributes().build());
		event.put(ModEntities.meat_idol.get(), EntityMeatIdol.createAttributes().build());
		event.put(ModEntities.abandoned_murderer.get(), EntityAbandonedMurderer.createAttributes().build());
		event.put(ModEntities.large_bird.get(), EntityLargeBird.createAttributes().build());
		event.put(ModEntities.punishing_bird.get(), EntityPunishingBird.createAttributes().build());
		event.put(ModEntities.approval_bird.get(), EntityApprovalBird.createAttributes().build());
		event.put(ModEntities.fourth_match_flame.get(), EntityFourthMatchFlame.createAttributes().build());
		event.put(ModEntities.wingbeat.get(), EntityWingBeat.createAttributes().build());
		event.put(ModEntities.snowqueen.get(), EntitySnowQueen.createAttributes().build());
		event.put(ModEntities.ppodae.get(), EntityPpodae.createAttributes().build());
		event.put(ModEntities.gallows.get(), EntityPpodae.createAttributes().build());
		event.put(ModEntities.children_galaxy.get(), EntityChildrenGalaxy.createAttributes().build());
		event.put(ModEntities.blue_star.get(), EntityBlueStar.createAttributes().build());
		event.put(ModEntities.thorn_bus.get(), EntityThornBus.createAttributes().build());
		event.put(ModEntities.black_forest_door.get(), EntityBlackForestDoor.createAttributes().build());
		event.put(ModEntities.end_bird_egg_eye.get(), EntityEndBirdEggEye.createAttributes().build());
		event.put(ModEntities.end_bird_egg_high.get(), EntityEndBirdEggHigh.createAttributes().build());
		event.put(ModEntities.end_bird_egg_small.get(), EntityEndBirdEggSmall.createAttributes().build());
		event.put(ModEntities.end_bird.get(), EntityEndBird.createAttributes().build());
	}
}
