package com.wzz.lobotocraft.compat.ysm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class YsmOptionalClassloadingTest {
    @Test
    void bridgeLoadsWithoutYsmOnTheTestClasspath() {
        assertDoesNotThrow(() -> Class.forName(YsmEmployeeCompat.class.getName(), false,
                YsmEmployeeCompat.class.getClassLoader()));
        assertDoesNotThrow(() -> Class.forName(YsmClassLocator.class.getName(), false,
                YsmClassLocator.class.getClassLoader()));
        assertDoesNotThrow(() -> Class.forName(
                "com.wzz.lobotocraft.client.compat.YsmClientCompat", false,
                YsmEmployeeCompat.class.getClassLoader()));
    }
}
