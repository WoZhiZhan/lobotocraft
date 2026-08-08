package com.wzz.lobotocraft.client.performance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientPerformancePolicyTest {
    @Test
    void classifiesThresholdBoundaries() {
        assertEquals(ClientPerformancePolicy.NORMAL,
                ClientPerformancePolicy.classify(TimeUnit.MILLISECONDS.toNanos(99)));
        assertEquals(ClientPerformancePolicy.SLOW,
                ClientPerformancePolicy.classify(TimeUnit.MILLISECONDS.toNanos(100)));
        assertEquals(ClientPerformancePolicy.SEVERE,
                ClientPerformancePolicy.classify(TimeUnit.MILLISECONDS.toNanos(250)));
        assertEquals(ClientPerformancePolicy.FREEZE,
                ClientPerformancePolicy.classify(TimeUnit.MILLISECONDS.toNanos(1_000)));
    }

    @Test
    void moreSevereReportsUseShorterThrottleWindows() {
        assertEquals(TimeUnit.SECONDS.toNanos(5),
                ClientPerformancePolicy.reportThrottleNanos(ClientPerformancePolicy.SLOW));
        assertEquals(TimeUnit.SECONDS.toNanos(2),
                ClientPerformancePolicy.reportThrottleNanos(ClientPerformancePolicy.SEVERE));
        assertEquals(TimeUnit.SECONDS.toNanos(1),
                ClientPerformancePolicy.reportThrottleNanos(ClientPerformancePolicy.FREEZE));
    }

    @Test
    void severityNamesAreStableForReports() {
        assertEquals("NORMAL", ClientPerformancePolicy.name(ClientPerformancePolicy.NORMAL));
        assertEquals("SLOW", ClientPerformancePolicy.name(ClientPerformancePolicy.SLOW));
        assertEquals("SEVERE", ClientPerformancePolicy.name(ClientPerformancePolicy.SEVERE));
        assertEquals("FREEZE", ClientPerformancePolicy.name(ClientPerformancePolicy.FREEZE));
    }
}
