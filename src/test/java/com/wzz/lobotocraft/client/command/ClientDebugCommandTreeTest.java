package com.wzz.lobotocraft.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientDebugCommandTreeTest {
    @Test
    void exposesPerformanceDiagnostics() {
        var root = ClientDebugCommand.build().build();
        var performance = root.getChild("performance");
        assertNotNull(performance);
        assertNotNull(performance.getChild("status"));
        assertNotNull(performance.getChild("dump"));
        assertNotNull(performance.getChild("reset"));
        assertNotNull(performance.getChild("path"));
    }
}
