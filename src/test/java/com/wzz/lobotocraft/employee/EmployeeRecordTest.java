package com.wzz.lobotocraft.employee;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class EmployeeRecordTest {
    @Test
    void manualStationSurvivesSaveAndLoad() {
        EmployeeRecord source = new EmployeeRecord(UUID.randomUUID(), "Stationed", EmployeeDepartment.CONTROL);
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");
        BlockPos station = new BlockPos(154, 64, 270);
        source.setManualStation(dimension, station);

        EmployeeRecord loaded = EmployeeRecord.load(source.save());

        assertEquals(dimension, loaded.manualStationDimension());
        assertEquals(station, loaded.manualStationPos());
    }

    @Test
    void oldRecordWithoutManualStationRemainsCompatible() {
        EmployeeRecord source = new EmployeeRecord(UUID.randomUUID(), "Legacy", EmployeeDepartment.CONTROL);

        EmployeeRecord loaded = EmployeeRecord.load(source.save());

        assertNull(loaded.manualStationDimension());
        assertNull(loaded.manualStationPos());
    }

    @Test
    void statValuesAreClampedOnRuntimeUpdateAndLoad() {
        EmployeeRecord source = new EmployeeRecord(UUID.randomUUID(), "Bounded", EmployeeDepartment.CONTROL);
        assertEquals(20, source.fortitude());
        source.setStats(10, 30, 100, 999);
        assertEquals(20, source.fortitude());
        assertEquals(110, source.justice());

        var edited = source.save();
        edited.putInt("Fortitude", -1);
        edited.putInt("Prudence", 111);
        EmployeeRecord loaded = EmployeeRecord.load(edited);
        assertEquals(20, loaded.fortitude());
        assertEquals(110, loaded.prudence());
    }

    @Test
    void optionalYsmAppearanceSurvivesSaveAndLoad() {
        EmployeeRecord source = new EmployeeRecord(UUID.randomUUID(), "Modeled",
                EmployeeDepartment.CONTROL);
        EmployeeAppearance appearance = new EmployeeAppearance(
                EmployeeAppearance.MODERN_YSM_PROVIDER,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "employees/control/clerk", "uniform_a");
        source.setAppearance(appearance);

        EmployeeRecord loaded = EmployeeRecord.load(source.save());

        assertEquals(appearance, loaded.appearance());
    }

    @Test
    void oldRecordWithoutAppearanceUsesDefaultRenderer() {
        EmployeeRecord source = new EmployeeRecord(UUID.randomUUID(), "Unmodeled",
                EmployeeDepartment.CONTROL);
        var saved = source.save();
        saved.remove("Appearance");

        EmployeeRecord loaded = EmployeeRecord.load(saved);

        assertEquals(EmployeeAppearance.DEFAULT, loaded.appearance());
    }
}
