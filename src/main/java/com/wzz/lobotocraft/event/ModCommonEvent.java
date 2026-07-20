package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityCoreSuppressionNpc;
import com.wzz.lobotocraft.entity.EntityGallows;
import com.wzz.lobotocraft.entity.EntityRedShoesClerk;
import com.wzz.lobotocraft.entity.abnormality.*;
import com.wzz.lobotocraft.entity.ordeal.EntityAmberDawn;
import com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall;
import com.wzz.lobotocraft.entity.ordeal.EntityCrimsonNoon;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenNoon;
import com.wzz.lobotocraft.entity.ordeal.EntityVioletDawn;
import com.wzz.lobotocraft.entity.ordeal.EntityVioletNoon;
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
		event.put(ModEntities.malkuth.get(), EntityCoreSuppressionNpc.createAttributes().build());
		event.put(ModEntities.yesod.get(), EntityCoreSuppressionNpc.createAttributes().build());
		event.put(ModEntities.hod.get(), EntityCoreSuppressionNpc.createAttributes().build());
		event.put(ModEntities.netzach.get(), EntityCoreSuppressionNpc.createAttributes().build());
		event.put(ModEntities.red_shoes_clerk.get(), EntityRedShoesClerk.createAttributes().build());
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
		event.put(ModEntities.army_in_black.get(), EntityArmyInBlack.createAttributes().build());
		event.put(ModEntities.crumbling_armor.get(), EntityCrumblingArmor.createAttributes().build());
		event.put(ModEntities.the_lady_facing_the_wall.get(), EntityLadyFacingTheWall.createAttributes().build());
		event.put(ModEntities.leticia.get(), EntityLeticia.createAttributes().build());
		event.put(ModEntities.leticia_friend.get(), EntityLeticiaFriend.createAttributes().build());
		event.put(ModEntities.bloody_small.get(), EntityBloodySmall.createAttributes().build());
		event.put(ModEntities.green_dawn.get(), EntityGreenDawn.createAttributes().build());
		event.put(ModEntities.violet_dawn.get(), EntityVioletDawn.createAttributes().build());
		event.put(ModEntities.violet_noon.get(), EntityVioletNoon.createAttributes().build());
		event.put(ModEntities.green_noon.get(), EntityGreenNoon.createAttributes().build());
		event.put(ModEntities.crimson_noon.get(), EntityCrimsonNoon.createAttributes().build());
		event.put(ModEntities.amber_dawn.get(), EntityAmberDawn.createAttributes().build());
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
		event.put(ModEntities.queen_bee.get(), EntityQueenBee.createAttributes().build());
		event.put(ModEntities.red_shoes.get(), EntityRedShoes.createAttributes().build());
		event.put(ModEntities.worker_bee.get(), EntityWorkerBee.createAttributes().build());
		event.put(ModEntities.snowqueen.get(), EntitySnowQueen.createAttributes().build());
		event.put(ModEntities.ppodae.get(), EntityPpodae.createAttributes().build());
		event.put(ModEntities.gallows.get(), EntityGallows.createAttributes().build());
		event.put(ModEntities.children_galaxy.get(), EntityChildrenGalaxy.createAttributes().build());
		event.put(ModEntities.blue_star.get(), EntityBlueStar.createAttributes().build());
		event.put(ModEntities.thorn_bus.get(), EntityThornBus.createAttributes().build());
		event.put(ModEntities.black_forest_door.get(), EntityBlackForestDoor.createAttributes().build());
		event.put(ModEntities.end_bird_egg_eye.get(), EntityEndBirdEggEye.createAttributes().build());
		event.put(ModEntities.end_bird_egg_high.get(), EntityEndBirdEggHigh.createAttributes().build());
		event.put(ModEntities.end_bird_egg_small.get(), EntityEndBirdEggSmall.createAttributes().build());
		event.put(ModEntities.end_bird.get(), EntityEndBird.createAttributes().build());
		event.put(ModEntities.nothing_there.get(), EntityNothingThere.createAttributes().build());
		event.put(ModEntities.smiling_corpse_mountain.get(), EntitySmilingCorpseMountain.createAttributes().build());
	}
}
