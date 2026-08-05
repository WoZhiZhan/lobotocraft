package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.capability.PlayerAbnormalityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgoItemSpecTest {
    private static final String ITEM = "lobotocraft:butterfly_funeral_weapon";

    @Test
    void roundTripPreservesStableVariantButRejectsRuntimeAndInjectedNbt() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("BFBlack", true);
        source.putInt("UseTick", 999999);
        source.putString("InjectedOperatorData", "must not persist");

        EgoItemSpec original = new EgoItemSpec(ITEM, source);
        EgoItemSpec restored = EgoItemSpec.load(original.save());

        assertEquals(original, restored);
        assertTrue(restored.initialTag().getBoolean("BFBlack"));
        assertFalse(restored.initialTag().contains("UseTick"));
        assertFalse(restored.initialTag().contains("InjectedOperatorData"));
    }

    @Test
    void whiteAndBlackRemainDistinctStableVariants() {
        CompoundTag black = new CompoundTag();
        black.putBoolean("BFBlack", true);
        EgoItemSpec whiteSpec = new EgoItemSpec(ITEM, null);
        EgoItemSpec blackSpec = new EgoItemSpec(ITEM, black);

        assertNotEquals(whiteSpec.variantId(), blackSpec.variantId());
        assertFalse(whiteSpec.initialTag().getBoolean("BFBlack"));
        assertTrue(blackSpec.initialTag().getBoolean("BFBlack"));
    }

    @Test
    void legacySolemnLamentDuplicateIdsMigrateToWhiteThenBlack() {
        List<EgoItemSpec> migrated = EgoItemSpec.normalizeLegacyVariants(
                "T-01-68", "weapon", EgoItemSpec.fromItemIds(List.of(ITEM, ITEM)));

        assertEquals(2, migrated.size());
        assertFalse(migrated.get(0).initialTag().getBoolean("BFBlack"));
        assertTrue(migrated.get(1).initialTag().getBoolean("BFBlack"));
        assertNotEquals(migrated.get(0), migrated.get(1));
    }

    @Test
    void migrationDoesNotRewriteOtherResearchOrUnexpectedCounts() {
        List<EgoItemSpec> one = EgoItemSpec.fromItemIds(List.of(ITEM));
        List<EgoItemSpec> otherCode = EgoItemSpec.normalizeLegacyVariants("O-00-00", "weapon",
                EgoItemSpec.fromItemIds(List.of(ITEM, ITEM)));

        assertEquals(one, EgoItemSpec.normalizeLegacyVariants("T-01-68", "weapon", one));
        assertEquals(otherCode.get(0), otherCode.get(1));
    }

    @Test
    void playerLegacySaveMigratesAndNewFormatSurvivesSecondRoundTrip() {
        CompoundTag legacy = legacyResearchTag();
        PlayerAbnormalityData first = new PlayerAbnormalityData();
        first.loadNBTData(legacy);

        List<EgoItemSpec> migrated = first.getPersonalEgoItemSpecs("T-01-68", "weapon");
        assertEquals(2, migrated.size());
        assertFalse(migrated.get(0).initialTag().getBoolean("BFBlack"));
        assertTrue(migrated.get(1).initialTag().getBoolean("BFBlack"));

        CompoundTag saved = first.serializeNBT();
        CompoundTag entry = saved.getList("PersonalEgoResearch", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(2, entry.getList("ItemSpecs", Tag.TAG_COMPOUND).size());

        PlayerAbnormalityData second = new PlayerAbnormalityData();
        second.deserializeNBT(saved);
        assertEquals(migrated, second.getPersonalEgoItemSpecs("T-01-68", "weapon"));
    }

    @Test
    void aiRepositoryAlsoMigratesLegacyDuplicateVariantStock() {
        CompoundTag root = new CompoundTag();
        CompoundTag stock = new CompoundTag();
        stock.putString("AbnormalityCode", "T-01-68");
        stock.putString("EquipmentType", "weapon");
        stock.putInt("Total", 1);
        stock.putInt("Maximum", 1);
        ListTag items = new ListTag();
        items.add(StringTag.valueOf(ITEM));
        items.add(StringTag.valueOf(ITEM));
        stock.put("Items", items);
        ListTag stocks = new ListTag();
        stocks.add(stock);
        root.put("Stocks", stocks);

        AiEgoRepositoryData data = AiEgoRepositoryData.load(root);
        List<EgoItemSpec> specs = data.stock("T-01-68", "weapon").itemSpecs();

        assertFalse(specs.get(0).initialTag().getBoolean("BFBlack"));
        assertTrue(specs.get(1).initialTag().getBoolean("BFBlack"));
        assertEquals(2, data.save(new CompoundTag()).getList("Stocks", Tag.TAG_COMPOUND)
                .getCompound(0).getList("ItemSpecs", Tag.TAG_COMPOUND).size());
    }

    private static CompoundTag legacyResearchTag() {
        CompoundTag entry = new CompoundTag();
        entry.putString("AbnormalityCode", "T-01-68");
        entry.putString("EquipmentType", "weapon");
        ListTag items = new ListTag();
        items.add(StringTag.valueOf(ITEM));
        items.add(StringTag.valueOf(ITEM));
        entry.put("Items", items);
        ListTag research = new ListTag();
        research.add(entry);
        CompoundTag root = new CompoundTag();
        root.put("PersonalEgoResearch", research);
        return root;
    }
}
