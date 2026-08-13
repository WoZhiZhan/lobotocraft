package com.wzz.lobotocraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExhibitionCommandTreeTest {
    @Test
    void exposesOperatorRecoveryActions() {
        var root = new ExhibitionCommand().build().build();
        for (String action : new String[]{
                "status", "start", "recover", "stop",
                "stop-work", "clear-status", "return-abnormalities",
                "reset-ai", "cleanup-events", "gauge", "max-ai"
        }) {
            assertNotNull(root.getChild(action), action);
        }
    }
}
