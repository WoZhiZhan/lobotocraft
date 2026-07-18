package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.model.block.GenericGeoBlockModel;
import com.wzz.lobotocraft.client.model.item.FlatCurioModel;
import com.wzz.lobotocraft.client.renderer.entity.EntityClerkRenderer;
import com.wzz.lobotocraft.client.renderer.entity.EntityLightOrbRenderer;
import com.wzz.lobotocraft.client.renderer.entity.abnormality.BlackForestDoorRenderer;
import com.wzz.lobotocraft.client.renderer.entity.EmptyEntityRenderer;
import com.wzz.lobotocraft.client.renderer.entity.GeoEntityRenderer;
import com.wzz.lobotocraft.client.renderer.block.BaseGeoBlockRenderer;
import com.wzz.lobotocraft.client.renderer.block.SimpleBlockEntityRenderer;
import com.wzz.lobotocraft.client.renderer.entity.abnormality.*;
import com.wzz.lobotocraft.client.renderer.item.BaseEgoCurioRenderer;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModShaders;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvent {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModItems.REGISTRY.getEntries().forEach(reg -> {
            Item item = reg.get();
            if (item instanceof BaseEgoCurio curio) {
                CuriosRendererRegistry.register(item,
                        () -> new BaseEgoCurioRenderer(curio.curioName()));
            }
        }));
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof BaseEgoCurio curio) || !curio.render2D()) continue;
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) continue;
            ModelResourceLocation mrl = new ModelResourceLocation(itemId, "inventory");
            BakedModel original = event.getModels().get(mrl);
            if (original == null) continue;
            ResourceLocation tex = ResourceUtil.createInstance(itemId.getNamespace(),
                    "item/" + curio.curioName() + "_curio");
            event.getModels().put(mrl, new FlatCurioModel(original, tex));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRegisterShaders(RegisterShadersEvent event) {
        ModShaders.onRegisterShaders(event);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.onebad.get(), AbnormalityRenderer::new);
        event.registerEntityRenderer(ModEntities.clerk.get(), EntityClerkRenderer::new);
        event.registerEntityRenderer(ModEntities.red_shoes_clerk.get(), EntityClerkRenderer::new);
        event.registerEntityRenderer(ModEntities.happy_teddy.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 0.65f));
        event.registerEntityRenderer(ModEntities.skadi_corrupted.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.isharmla.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.isharmla_tear.get(), (r) -> new GeoEntityRenderer<>(r, 0.8f, 0.6f));
        event.registerEntityRenderer(ModEntities.shell_sea_runner.get(), (r) -> new GeoEntityRenderer<>(r, 0.7f, 0.7f));
        event.registerEntityRenderer(ModEntities.deepsea_slider.get(), (r) -> new GeoEntityRenderer<>(r, 0.7f, 0.6f));
        event.registerEntityRenderer(ModEntities.ridgesea_spitter.get(), (r) -> new GeoEntityRenderer<>(r, 0.7f, 0.8f));
        event.registerEntityRenderer(ModEntities.primalsea_piercer.get(), (r) -> new GeoEntityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.nucleic_maleficent.get(), (r) -> new GeoEntityRenderer<>(r, 1.2f, 1.2f));
        event.registerEntityRenderer(ModEntities.basinsea_reaper.get(), (r) -> new GeoEntityRenderer<>(r, 1.4f, 1.4f));
        event.registerEntityRenderer(ModEntities.cleaner.get(), (r) -> new GeoEntityRenderer<>(r, 1.01f, 1.0f));
        event.registerEntityRenderer(ModEntities.army_in_black.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.crumbling_armor.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.the_lady_facing_the_wall.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.leticia.get(), (r) -> new AbnormalityRenderer<>(r, 0.9f, 0.9f));
        event.registerEntityRenderer(ModEntities.leticia_friend.get(), (r) -> new GeoEntityRenderer<>(r, 0.9f, 0.9f));
        event.registerEntityRenderer(ModEntities.bloody_small.get(), (r) -> new GeoEntityRenderer<>(r, 0.5f, 0.5f));
        event.registerEntityRenderer(ModEntities.green_dawn.get(), (r) -> new GeoEntityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.violet_dawn.get(), (r) -> new GeoEntityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.violet_noon.get(), (r) -> new GeoEntityRenderer<>(r, 2.0f, 2.0f));
        event.registerEntityRenderer(ModEntities.green_noon.get(), (r) -> new GeoEntityRenderer<>(r, 1.1f, 1.1f));
        event.registerEntityRenderer(ModEntities.crimson_noon.get(), (r) -> new GeoEntityRenderer<>(r, 1.2f, 1.2f));
        event.registerEntityRenderer(ModEntities.amber_dawn.get(), (r) -> new GeoEntityRenderer<>(r, 0.8f, 0.8f));
        event.registerEntityRenderer(ModEntities.butterfly_funeral.get(), (r) -> new AbnormalityRenderer<>(r, "butterfly", "butterfly", 0.6f, 0.6f));
        event.registerEntityRenderer(ModEntities.fragment_of_the_universe.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.helper.get(), (r) -> new AbnormalityRenderer<>(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.bigbadwolf.get(), (r) -> new com.wzz.lobotocraft.client.renderer.entity.abnormality.EntityBigBadWolfRenderer(r, 1.0f, 1.0f));
        event.registerEntityRenderer(ModEntities.redhat_mercenary.get(), (r) -> new AbnormalityRenderer<>(r, "redhat", "redhat", 0.72f, 0.72f));
        event.registerEntityRenderer(ModEntities.iron_maiden.get(), (r) -> new AbnormalityRenderer<>(r, 1.5f, 1.5f));
        event.registerEntityRenderer(ModEntities.meat_idol.get(), (r) -> new AbnormalityRenderer<>(r, 1.2f, 1.0f));
        event.registerEntityRenderer(ModEntities.abandoned_murderer.get(), (r) -> new AbnormalityRenderer<>(r, 0.8f, 0.8f));
        event.registerBlockEntityRenderer(ModBlockEntities.REGENERATION_REACTOR.get(), context -> new BaseGeoBlockRenderer(
                new GenericGeoBlockModel<>("regeneration_reactor.geo.json", "regeneration_reactor.png", "regeneration_reactor.animation.json")
        ));
        event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR.get(), context -> new BaseGeoBlockRenderer(
                new GenericGeoBlockModel<>("elevator.geo.json", "elevator.png", "elevator.animation.json")
        ));
        event.registerBlockEntityRenderer(ModBlockEntities.ESCAPE.get(), SimpleBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PUNISHING_BIRD.get(), context -> new BaseGeoBlockRenderer(
                new GenericGeoBlockModel<>("punishing_bird.geo.json", "punishing_bird.png", null)
        ).withScale(2f, 1f).withOffset(-0.25f, 0f, -0.25f));
        event.registerBlockEntityRenderer(ModBlockEntities.TOMBSTONE.get(), context -> new BaseGeoBlockRenderer(
                new GenericGeoBlockModel<>("tombstone.geo.json", "tombstone.png", null)
        ));
        event.registerEntityRenderer(ModEntities.immortal_item.get(), ItemEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.large_bird.get(), (r) -> new EntityLargeBirdRenderer(r, 2f, 2f));
        event.registerEntityRenderer(ModEntities.light_follower.get(), EmptyEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.punishing_bird.get(), (r) -> new EntityPunishingBirdRenderer(r, 1.5f, 1.5f));
        event.registerEntityRenderer(ModEntities.approval_bird.get(), (r) -> new AbnormalityRenderer<>(r, 1.5f, 2f));
        event.registerEntityRenderer(ModEntities.fourth_match_flame.get(), (r) -> new EntityFourthMatchFlameRenderer(r, 0.8f, 1.0f));
        event.registerEntityRenderer(ModEntities.wingbeat.get(), (r) -> new EntityWingBestRenderer(r, 1.2f, 0.9f));
        event.registerEntityRenderer(ModEntities.queen_bee.get(), (r) -> new AbnormalityRenderer<>(r, 1.2f, 1.2f));
        event.registerEntityRenderer(ModEntities.red_shoes.get(), (r) -> new AbnormalityRenderer<>(r, 0.8f, 0.8f));
        event.registerEntityRenderer(ModEntities.worker_bee.get(), (r) -> new GeoEntityRenderer<>(r, 0.9f, 0.9f));
        event.registerEntityRenderer(ModEntities.snowqueen.get(), (r) -> new AbnormalityRenderer<>(r, 1.6f, 1.2f));
        event.registerEntityRenderer(ModEntities.ppodae.get(), (r) -> new EntityPpodaeRenderer(r, 0.9f, 0.8f));
        event.registerEntityRenderer(ModEntities.gallows.get(), (r) -> new GeoEntityRenderer<>(r, 1.4f, 1f));
        event.registerEntityRenderer(ModEntities.children_galaxy.get(), (r) -> new AbnormalityRenderer<>(r, 0.8f, 0.8f));
        event.registerEntityRenderer(ModEntities.blue_star.get(), (r) -> new AbnormalityRenderer<>(r, 2.02f, 2f));
        event.registerEntityRenderer(ModEntities.thorn_bus.get(), (r) -> new AbnormalityRenderer<>(r, 1.2f, 0.9f));
        event.registerEntityRenderer(ModEntities.black_forest_door.get(), BlackForestDoorRenderer::new);
        event.registerEntityRenderer(ModEntities.end_bird_egg_eye.get(), (r) -> new AbnormalityRenderer<>(r,
                "end_bird_egg_eye", "end_bird_egg", 1.2f, 0.9f));
        event.registerEntityRenderer(ModEntities.end_bird_egg_high.get(), (r) -> new AbnormalityRenderer<>(r,
                "end_bird_egg_high", "end_bird_egg", 1.2f, 0.9f));
        event.registerEntityRenderer(ModEntities.end_bird_egg_small.get(), (r) -> new AbnormalityRenderer<>(r,
                "end_bird_egg_small", "end_bird_egg", 1.2f, 0.9f));
        event.registerEntityRenderer(ModEntities.end_bird.get(), (r) -> new AbnormalityRenderer<>(r, 3f, 3f));
        event.registerEntityRenderer(ModEntities.light_orb.get(), EntityLightOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.water_spit.get(), EmptyEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.nothing_there.get(),
                (r) -> new EntityNothingThereRenderer(r, 0.9f));
        event.registerEntityRenderer(ModEntities.smiling_corpse_mountain.get(), (r) -> new AbnormalityRenderer<>(r, 0.8f, 0.8f));
    }
}
