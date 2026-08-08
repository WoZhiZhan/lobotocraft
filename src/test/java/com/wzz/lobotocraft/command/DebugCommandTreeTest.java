package com.wzz.lobotocraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DebugCommandTreeTest {
    @Test
    void exposesAllModernDebugGroups() {
        var root = new DebugCommand().build().build();
        for (String group : new String[]{
                "status", "work", "employee", "abnormality", "room",
                "elevator", "facility", "counter", "ordeal", "suppression",
                "department", "ego", "performance"
        }) {
            assertNotNull(root.getChild(group), group);
        }

        assertNotNull(root.getChild("work").getChild("force-complete"));
        assertNotNull(root.getChild("work").getChild("start"));
        assertNotNull(root.getChild("employee").getChild("cancel"));
        assertNotNull(root.getChild("employee").getChild("work"));
        assertNotNull(root.getChild("employee").getChild("suppress"));
        assertNotNull(root.getChild("employee").getChild("follow"));
        assertNotNull(root.getChild("employee").getChild("tool"));
        assertNotNull(root.getChild("employee").getChild("spawn"));
        assertNotNull(root.getChild("abnormality").getChild("register-check"));
        assertNotNull(root.getChild("abnormality").getChild("qliphoth"));
        assertNotNull(root.getChild("abnormality").getChild("escape"));
        assertNotNull(root.getChild("abnormality").getChild("return"));
        assertNotNull(root.getChild("abnormality").getChild("heal"));
        assertNotNull(root.getChild("facility").getChild("trigger-stage"));
        assertNotNull(root.getChild("facility").getChild("requirements"));
        assertNotNull(root.getChild("facility").getChild("energy"));
        assertNotNull(root.getChild("facility").getChild("gauge"));
        assertNotNull(root.getChild("counter").getChild("set"));
        assertNotNull(root.getChild("counter").getChild("add"));
        assertNotNull(root.getChild("ordeal").getChild("status"));
        assertNotNull(root.getChild("ordeal").getChild("trigger"));
        assertNotNull(root.getChild("suppression").getChild("status"));
        assertNotNull(root.getChild("department").getChild("set"));
    }
}
