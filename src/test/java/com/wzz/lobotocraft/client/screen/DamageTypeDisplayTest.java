package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.screen.config.EncyclopediaGUIConfig;
import com.wzz.lobotocraft.client.ui.DamageTypeVisual;
import com.wzz.lobotocraft.entity.data.EgoWeaponDisplayCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DamageTypeDisplayTest {
    @Test
    void legacyBlueAndOfficialPaleUseTheSameDisplayColor() {
        assertEquals(EncyclopediaGUIConfig.Colors.getDamageTypeColor("PALE"),
                EncyclopediaGUIConfig.Colors.getDamageTypeColor("BLUE"));
        assertEquals(DamageTypeVisual.PALE, DamageTypeVisual.from("blue"));
        assertEquals(DamageTypeVisual.PALE, DamageTypeVisual.from(" pale "));
        assertEquals("PALE", DamageTypeVisual.label("BLUE"));
        assertEquals("PALE", DamageTypeVisual.label("pale"));
        assertEquals("ALL", DamageTypeVisual.label("ALL"));
        assertNull(DamageTypeVisual.UNKNOWN.texturePath());
    }

    @Test
    void fallbackEgoCatalogLegacyTypePresentsOfficialDamageName() {
        assertEquals("PALE", DamageTypeVisual.label(EgoWeaponDisplayCatalog.find("0-02-62").damageType()));
        assertEquals("PALE", DamageTypeVisual.label(EgoWeaponDisplayCatalog.find("O-05-61").damageType()));
    }

    @Test
    void everyDamageTypeUsesTheOriginalInventoryIconWithItsFullDimensions() {
        for (DamageTypeVisual type : new DamageTypeVisual[] {
                DamageTypeVisual.RED, DamageTypeVisual.WHITE,
                DamageTypeVisual.BLACK, DamageTypeVisual.PALE
        }) {
            assertNotNull(getClass().getResource("/assets/lobotocraft/" + type.texturePath()),
                    type.name());
        }
        assertEquals("textures/gui/inventory/damage_pale.png", DamageTypeVisual.from("BLUE").texturePath());
        assertEquals(116, DamageTypeVisual.PALE.textureWidth());
        assertEquals(120, DamageTypeVisual.PALE.textureHeight());
    }
}
