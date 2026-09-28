package com.wzz.lobotocraft.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LobotomyUiRendererCacheTest {
    @Test
    @SuppressWarnings("unchecked")
    void resourceReloadInvalidatesOldMissingTextureDecisions() throws Exception {
        Field field = LobotomyUiRenderer.class.getDeclaredField("TEXTURE_CACHE");
        field.setAccessible(true);
        Map<ResourceLocation, Boolean> cache = (Map<ResourceLocation, Boolean>) field.get(null);
        try {
            cache.put(ResourceLocation.fromNamespaceAndPath("lobotocraft", "textures/gui/theme/reload_probe.png"),
                    false);
            LobotomyUiRenderer.clearTextureCache();
            assertTrue(cache.isEmpty());
        } finally {
            cache.clear();
        }
    }
}
