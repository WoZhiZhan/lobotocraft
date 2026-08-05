package com.wzz.lobotocraft.compat.ysm;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YsmModelCatalogTest {
    @Test
    void authoritativeCatalogRoundTripsAndAcceptsIdOrPath() {
        YsmModelCatalog source = YsmModelCatalog.authoritative(List.of(
                "employees/control/clerk", "hash-123", "employees/control/clerk"));
        CompoundTag tag = new CompoundTag();
        source.writeTo(tag);

        YsmModelCatalog loaded = YsmModelCatalog.readFrom(tag);

        assertTrue(loaded.authoritative());
        assertEquals(2, loaded.keys().size());
        assertTrue(loaded.allows("hash-123", "missing"));
        assertTrue(loaded.allows("missing", "employees/control/clerk"));
        assertFalse(loaded.allows("missing", "also-missing"));
        assertEquals(List.of("local-only", "shared"), loaded.allowedTextures(
                "hash-123", "missing", List.of("local-only", "shared")));
    }

    @Test
    void authoritativeTexturesFilterClientOnlySelections() {
        YsmModelCatalog source = YsmModelCatalog.authoritative(Map.of(
                "hash-123", List.of("default", "shared.png"),
                "employees/control/clerk", List.of("default", "shared.png")));
        CompoundTag tag = new CompoundTag();
        source.writeTo(tag);

        YsmModelCatalog loaded = YsmModelCatalog.readFrom(tag);

        assertEquals(List.of("default", "shared"), loaded.allowedTextures(
                "hash-123", "employees/control/clerk",
                List.of("default", "client-only", "shared")));
        assertTrue(loaded.allowedTextures("missing", "also-missing",
                List.of("default")).isEmpty());
    }

    @Test
    void unknownCatalogPreservesCompatibilityFallback() {
        CompoundTag tag = new CompoundTag();
        YsmModelCatalog.unknown().writeTo(tag);

        YsmModelCatalog loaded = YsmModelCatalog.readFrom(tag);

        assertFalse(loaded.authoritative());
        assertTrue(loaded.allows("client-only-id", "client-only-path"));
        assertEquals(List.of("client-only"), loaded.allowedTextures(
                "client-only-id", "client-only-path", List.of("client-only")));
    }
}
