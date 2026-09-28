package com.wzz.lobotocraft.work;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AbnormalityCommandPolicyTest {
    @Test
    void onlyDeclaredSlotsBecomeUniqueCommands() {
        assertEquals(AbnormalityCommandPolicy.Action.SACRIFICE,
                AbnormalityCommandPolicy.action("O-03-89", 3));
        assertEquals(AbnormalityCommandPolicy.Action.COMMISSION,
                AbnormalityCommandPolicy.action("F-01-57", 2));
        assertEquals(AbnormalityCommandPolicy.Action.PROTECTION,
                AbnormalityCommandPolicy.action("D-01-106", 2));
        for (String code : new String[]{"O-03-89", "F-01-57", "D-01-106"}) {
            assertNull(AbnormalityCommandPolicy.action(code, 0));
            assertNull(AbnormalityCommandPolicy.action(code, 1));
        }
        assertNull(AbnormalityCommandPolicy.action("O-03-03", 3));
        assertNull(AbnormalityCommandPolicy.action("O-03-89", -1));
        assertNull(AbnormalityCommandPolicy.action("O-03-89", 4));
    }
}
