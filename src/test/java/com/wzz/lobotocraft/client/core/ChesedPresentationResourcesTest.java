package com.wzz.lobotocraft.client.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChesedPresentationResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources/assets/lobotocraft");

    @Test
    void chesedUsesADedicatedNonDistortingRainShader() throws IOException {
        String post = Files.readString(RESOURCES.resolve("shaders/post/core_chesed.json"));
        JsonParser.parseString(post);
        assertTrue(post.contains("lobotocraft:core_chesed_rain"));
        assertFalse(post.contains("core_water_drop"));
        assertFalse(post.contains("core_vignette"));

        String fragment = Files.readString(
                RESOURCES.resolve("shaders/program/core_chesed_rain.fsh"));
        JsonParser.parseString(Files.readString(
                RESOURCES.resolve("shaders/program/core_chesed_rain.json")));
        assertTrue(fragment.contains("texture(DiffuseSampler, texCoord)"));
        assertFalse(fragment.contains("distortedUv"));
        assertFalse(fragment.contains("Vignetting"));
    }

    @Test
    void chesedHudUsesTheOriginalEnabledAndDisabledMechanicSprites() throws IOException {
        Path textureRoot = RESOURCES.resolve("textures/gui/core_suppression/chesed/damage");
        List<String> textureNames = List.of(
                "red_enabled.png", "red_disabled.png",
                "white_enabled.png", "white_disabled.png",
                "black_enabled.png", "black_disabled.png",
                "pale_enabled.png", "pale_disabled.png",
                "core.png");
        for (int i = 0; i < textureNames.size(); i++) {
            String textureName = textureNames.get(i);
            Path texture = textureRoot.resolve(textureName);
            assertTrue(Files.isRegularFile(texture), textureName);
            BufferedImage image = ImageIO.read(texture.toFile());
            assertNotNull(image, textureName);
            if (i < textureNames.size() - 1) {
                assertEquals(96, image.getWidth(), textureName);
                assertEquals(96, image.getHeight(), textureName);
            }
        }

        String overlay = Files.readString(Path.of(
                "src/main/java/com/wzz/lobotocraft/client/core/CoreSuppressionOverlay.java"));
        assertTrue(overlay.contains("textures/gui/core_suppression/chesed/damage/"));
        assertFalse(overlay.contains("kind.id().toUpperCase()"));
    }
}
