package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.EntityLightOrb;
import com.wzz.lobotocraft.entity.abnormality.EntityBlackForestDoor;
import com.wzz.lobotocraft.entity.EntityGallows;
import com.wzz.lobotocraft.entity.EntityImmortalItem;
import com.wzz.lobotocraft.entity.EntityLightFollower;
import com.wzz.lobotocraft.entity.abnormality.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModMain.MODID);

    public static final RegistryObject<EntityType<EntityOneBad>> onebad =
            ENTITIES.register("onebad",
                    () -> EntityType.Builder.of(EntityOneBad::new, MobCategory.CREATURE)
                            .sized(1.2F, 2F)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("onebad"));

    public static final RegistryObject<EntityType<EntityHappyTeddy>> happy_teddy =
            ENTITIES.register("happy_teddy",
                    () -> EntityType.Builder.of(EntityHappyTeddy::new, MobCategory.CREATURE)
                            .sized(1.2F, 1.8F)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("happy_teddy"));

    public static final RegistryObject<EntityType<EntityDarkSkadi>> skadi_corrupted =
            ENTITIES.register("skadi_corrupted",
                    () -> EntityType.Builder.of(EntityDarkSkadi::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("skadi_corrupted"));

    public static final RegistryObject<EntityType<EntityIsharmla>> isharmla =
            ENTITIES.register("isharmla",
                    () -> EntityType.Builder.of(EntityIsharmla::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(12)
                            .updateInterval(3)
                            .build("isharmla"));

    public static final RegistryObject<EntityType<EntityIsharmlaTear>> isharmla_tear =
            ENTITIES.register("isharmla_tear",
                    () -> EntityType.Builder.of(EntityIsharmlaTear::new, MobCategory.MONSTER)
                            .sized(0.8F, 1.2F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("isharmla_tear"));

    public static final RegistryObject<EntityType<EntityHelper>> helper =
            ENTITIES.register("helper",
                    () -> EntityType.Builder.of(EntityHelper::new, MobCategory.CREATURE)
                            .sized(0.9F, 1.3F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("helper"));

    public static final RegistryObject<EntityType<EntityFragmentOfUniverse>> fragment_of_the_universe =
            ENTITIES.register("fragment_of_the_universe",
                    () -> EntityType.Builder.of(EntityFragmentOfUniverse::new, MobCategory.CREATURE)
                            .sized(3.0F, 5.0F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("fragment_of_the_universe"));

    public static final RegistryObject<EntityType<EntityBigBadWolf>> bigbadwolf =
            ENTITIES.register("bigbadwolf",
                    () -> EntityType.Builder.of(EntityBigBadWolf::new, MobCategory.CREATURE)
                            .sized(2.6F, 4.1F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("bigbadwolf"));

    public static final RegistryObject<EntityType<EntityButterflyFuneral>> butterfly_funeral =
            ENTITIES.register("butterfly_funeral",
                    () -> EntityType.Builder.of(EntityButterflyFuneral::new, MobCategory.CREATURE)
                            .sized(1.4F, 2.7F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("butterfly_funeral"));

    public static final RegistryObject<EntityType<EntityRedHoodMercenary>> redhat_mercenary =
            ENTITIES.register("redhat_mercenary",
                    () -> EntityType.Builder.of(EntityRedHoodMercenary::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("redhat_mercenary"));

    public static final RegistryObject<EntityType<EntityCleaner>> cleaner =
            ENTITIES.register("cleaner",
                    () -> EntityType.Builder.of(EntityCleaner::new, MobCategory.MONSTER)
                            .sized(0.7F, 1.95F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("cleaner"));

    // ============ 海嗣(深蓝色正午——大群的意志) ============
    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityShellSeaRunner>> shell_sea_runner =
            ENTITIES.register("shell_sea_runner",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityShellSeaRunner::new, MobCategory.MONSTER)
                            .sized(0.9F, 0.9F).clientTrackingRange(8).updateInterval(3).build("shell_sea_runner"));

    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityDeepSeaSlider>> deepsea_slider =
            ENTITIES.register("deepsea_slider",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityDeepSeaSlider::new, MobCategory.MONSTER)
                            .sized(0.9F, 0.8F).clientTrackingRange(8).updateInterval(3).build("deepsea_slider"));

    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityRidgeSeaSpitter>> ridgesea_spitter =
            ENTITIES.register("ridgesea_spitter",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityRidgeSeaSpitter::new, MobCategory.MONSTER)
                            .sized(0.9F, 1.0F).clientTrackingRange(10).updateInterval(3).build("ridgesea_spitter"));

    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityPrimalSeaPiercer>> primalsea_piercer =
            ENTITIES.register("primalsea_piercer",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityPrimalSeaPiercer::new, MobCategory.MONSTER)
                            .sized(1.2F, 1.4F).clientTrackingRange(10).updateInterval(3).build("primalsea_piercer"));

    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityNucleicMaleficent>> nucleic_maleficent =
            ENTITIES.register("nucleic_maleficent",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityNucleicMaleficent::new, MobCategory.MONSTER)
                            .sized(1.4F, 1.6F).clientTrackingRange(10).updateInterval(3).build("nucleic_maleficent"));

    public static final RegistryObject<EntityType<com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaReaper>> basinsea_reaper =
            ENTITIES.register("basinsea_reaper",
                    () -> EntityType.Builder.of(com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaReaper::new, MobCategory.MONSTER)
                            .sized(1.6F, 1.8F).clientTrackingRange(12).updateInterval(3).build("basinsea_reaper"));

    public static final RegistryObject<EntityType<EntityIronMaiden>> iron_maiden =
            ENTITIES.register("iron_maiden",
                    () -> EntityType.Builder.of(EntityIronMaiden::new, MobCategory.CREATURE)
                            .sized(1.5F, 2.2F)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("iron_maiden"));

    public static final RegistryObject<EntityType<EntityMeatIdol>> meat_idol =
            ENTITIES.register("meat_idol", () -> EntityType.Builder
                    .of(EntityMeatIdol::new, MobCategory.CREATURE)
                    .sized(2.0f, 1.0f)
                    .build("meat_idol")
            );

    public static final RegistryObject<EntityType<EntityAbandonedMurderer>> abandoned_murderer =
            ENTITIES.register("abandoned_murderer",
                    () -> EntityType.Builder.of(EntityAbandonedMurderer::new, MobCategory.CREATURE)
                            .sized(1F, 2.3F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("abandoned_murderer"));

    public static final RegistryObject<EntityType<EntityImmortalItem>> immortal_item =
            ENTITIES.register("immortal_item",
                    () -> EntityType.Builder
                            .of(EntityImmortalItem::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("immortal_item"));

    public static final RegistryObject<EntityType<EntityLargeBird>> large_bird =
            ENTITIES.register("large_bird",
                    () -> EntityType.Builder.of(EntityLargeBird::new, MobCategory.CREATURE)
                            .sized(6.0F, 6.0F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("large_bird"));

    public static final RegistryObject<EntityType<EntityLightFollower>> light_follower =
            ENTITIES.register("light_follower",
                    () -> EntityType.Builder
                            .of(EntityLightFollower::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(6)
                            .updateInterval(32)
                            .build("light_follower"));

    public static final RegistryObject<EntityType<EntityPunishingBird>> punishing_bird =
            ENTITIES.register("punishing_bird",
                    () -> EntityType.Builder.of(EntityPunishingBird::new, MobCategory.MONSTER)
                            .sized(0.8F, 0.8F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("punishing_bird"));

    public static final RegistryObject<EntityType<EntityApprovalBird>> approval_bird =
            ENTITIES.register("approval_bird",
                    () -> EntityType.Builder.of(EntityApprovalBird::new, MobCategory.CREATURE)
                            .sized(2.5F, 3F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("approval_bird"));

    public static final RegistryObject<EntityType<EntityFourthMatchFlame>> fourth_match_flame =
            ENTITIES.register("fourth_match_flame",
                    () -> EntityType.Builder.of(EntityFourthMatchFlame::new, MobCategory.CREATURE)
                            .sized(0.8F, 1.0F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("fourth_match_flame"));

    public static final RegistryObject<EntityType<EntityWingBeat>> wingbeat =
            ENTITIES.register("wingbeat",
                    () -> EntityType.Builder.of(EntityWingBeat::new, MobCategory.MONSTER)
                            .sized(0.5F, 1.3F)
                            .clientTrackingRange(8)
                            .updateInterval(12)
                            .build("wingbeat"));

    public static final RegistryObject<EntityType<EntitySnowQueen>> snowqueen =
            ENTITIES.register("snowqueen",
                    () -> EntityType.Builder.of(EntitySnowQueen::new, MobCategory.CREATURE)
                            .sized(1.3F, 2.8F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("snowqueen"));

    public static final RegistryObject<EntityType<EntityPpodae>> ppodae =
            ENTITIES.register("ppodae",
                    () -> EntityType.Builder.of(EntityPpodae::new, MobCategory.CREATURE)
                            .sized(0.6F, 0.9F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("ppodae"));

    public static final RegistryObject<EntityType<EntityGallows>> gallows =
            ENTITIES.register("gallows",
                    () -> EntityType.Builder.of(EntityGallows::new, MobCategory.MONSTER)
                            .sized(0.6F, 0.9F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("gallows"));

    public static final RegistryObject<EntityType<EntityChildrenGalaxy>> children_galaxy =
            ENTITIES.register("children_galaxy",
                    () -> EntityType.Builder.of(EntityChildrenGalaxy::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.4F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("children_galaxy"));

    public static final RegistryObject<EntityType<EntityBlueStar>> blue_star =
            ENTITIES.register("blue_star",
                    () -> EntityType.Builder.of(EntityBlueStar::new, MobCategory.MONSTER)
                            .sized(1.8F, 1.8F)
                            .clientTrackingRange(8)
                            .updateInterval(16)
                            .build("blue_star"));

    public static final RegistryObject<EntityType<EntityThornBus>> thorn_bus =
            ENTITIES.register("thorn_bus",
                    () -> EntityType.Builder.of(EntityThornBus::new, MobCategory.CREATURE)
                            .sized(1.2F, 2.4F)
                            .clientTrackingRange(8)
                            .updateInterval(16)
                            .build("thorn_bus"));

    public static final RegistryObject<EntityType<EntityBlackForestDoor>> black_forest_door =
            ENTITIES.register("black_forest_door",
                    () -> EntityType.Builder.of(EntityBlackForestDoor::new, MobCategory.MONSTER)
                            .sized(1.5F, 2.8F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("black_forest_door"));

    public static final RegistryObject<EntityType<EntityEndBirdEggEye>> end_bird_egg_eye =
            ENTITIES.register("end_bird_egg_eye",
                    () -> EntityType.Builder.of(EntityEndBirdEggEye::new, MobCategory.MONSTER)
                            .sized(1F, 1F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("end_bird_egg_eye"));

    public static final RegistryObject<EntityType<EntityEndBirdEggHigh>> end_bird_egg_high =
            ENTITIES.register("end_bird_egg_high",
                    () -> EntityType.Builder.of(EntityEndBirdEggHigh::new, MobCategory.MONSTER)
                            .sized(1F, 1F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("end_bird_egg_high"));

    public static final RegistryObject<EntityType<EntityEndBirdEggSmall>> end_bird_egg_small =
            ENTITIES.register("end_bird_egg_small",
                    () -> EntityType.Builder.of(EntityEndBirdEggSmall::new, MobCategory.MONSTER)
                            .sized(1F, 1F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("end_bird_egg_small"));

    public static final RegistryObject<EntityType<EntityEndBird>> end_bird =
            ENTITIES.register("end_bird",
                    () -> EntityType.Builder.of(EntityEndBird::new, MobCategory.MONSTER)
                            .sized(5F, 5F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("end_bird"));

    public static final RegistryObject<EntityType<EntityLightOrb>> light_orb =
            ENTITIES.register("light_orb", () ->
                    EntityType.Builder.of(EntityLightOrb::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("light_orb"));
}
