package com.wzz.lobotocraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EgoAuthorizationCommandTest {
    @Test
    void exposesPersonalAndAiAuthorizationOperations() {
        var root = new EgoAuthorizationCommand().build().build();
        assertNotNull(root.getChild("grant-all"));
        assertNotNull(root.getChild("grant-all").getChild("player"));
        assertNotNull(root.getChild("ai-max"));
    }
}
