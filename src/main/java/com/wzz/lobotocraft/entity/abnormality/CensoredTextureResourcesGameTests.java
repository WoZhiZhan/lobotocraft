package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.client.model.entity.BaseModel;
import com.wzz.lobotocraft.client.model.entity.CensoredModel;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(CensoredTextureResourcesGameTests.TEST_NAMESPACE)
public final class CensoredTextureResourcesGameTests {
    static final String TEST_NAMESPACE = "lobotocraft_v027_resources";

    private CensoredTextureResourcesGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 20, batch = "v027_resources")
    public static void containedTextureUsesTheAbnormalityDirectory(GameTestHelper helper) {
        EntityCensored entity = ModEntities.censored.get().create(helper.getLevel());
        helper.assertTrue(entity != null, "CENSORED fixture could not be created");
        try {
            entity.setEscape(false);
            CensoredModel model = new CensoredModel();
            ResourceLocation texture = model.getTextureResource(entity);
            assertResource(helper, texture);
            helper.assertTrue(texture.getPath().equals("textures/entities/abnormality/o_03_89.png"),
                    "Contained CENSORED requested " + texture);
            assertResource(helper, model.getModelResource(entity));
            assertResource(helper, model.getAnimationResource(entity));
            helper.succeed();
        } finally {
            entity.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 20, batch = "v027_resources")
    public static void escapedTextureUsesTheRealModelSelection(GameTestHelper helper) {
        EntityCensored entity = ModEntities.censored.get().create(helper.getLevel());
        helper.assertTrue(entity != null, "CENSORED fixture could not be created");
        try {
            entity.setEscape(true);
            CensoredModel model = new CensoredModel();
            ResourceLocation texture = model.getTextureResource(entity);
            helper.assertTrue(texture.getPath().equals("textures/entities/censored.png"),
                    "Escaped CENSORED requested " + texture);
            assertResource(helper, texture);
            assertResource(helper, model.getModelResource(entity));
            assertResource(helper, model.getAnimationResource(entity));
            helper.succeed();
        } finally {
            entity.discard();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 20, batch = "v027_resources")
    public static void minionTextureUsesItsSyncedEntityTexture(GameTestHelper helper) {
        EntityCensoredMinion entity = ModEntities.censored_minion.get().create(helper.getLevel());
        helper.assertTrue(entity != null, "CENSORED-1 fixture could not be created");
        try {
            BaseModel<EntityCensoredMinion> model = new BaseModel<>();
            ResourceLocation texture = model.getTextureResource(entity);
            helper.assertTrue(texture.getPath().equals("textures/entities/censored1.png"),
                    "CENSORED-1 requested " + texture);
            assertResource(helper, texture);
            assertResource(helper, model.getModelResource(entity));
            assertResource(helper, model.getAnimationResource(entity));
            helper.succeed();
        } finally {
            entity.discard();
        }
    }

    private static void assertResource(GameTestHelper helper, ResourceLocation resource) {
        String path = "/assets/" + resource.getNamespace() + "/" + resource.getPath();
        helper.assertTrue(CensoredTextureResourcesGameTests.class.getResource(path) != null,
                "Computed model resource is missing: " + resource);
    }
}
