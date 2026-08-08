package com.wzz.lobotocraft.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LlmHistoryDataTest {
    @Test
    void historiesRoundTripAndEmployeeIdentityUsesUuid() {
        UUID player = UUID.randomUUID();
        UUID oldEmployee = UUID.randomUUID();
        UUID replacementWithSameName = UUID.randomUUID();
        LlmHistoryData original = new LlmHistoryData();
        original.appendDispatch(player, "move", "accepted");
        original.appendDialogue(player, oldEmployee, "come", "follow", "on my way");
        original.appendDialogue(player, replacementWithSameName, "hello", "", "hello");
        original.appendSmallTalk(player, "status?", "all clear");

        LlmHistoryData loaded = LlmHistoryData.load(original.save(new CompoundTag()));
        assertEquals("move", loaded.dispatch(player).get(0).command());
        assertEquals("on my way", loaded.dialogue(player, oldEmployee).get(0).reply());
        assertEquals("hello", loaded.dialogue(player, replacementWithSameName).get(0).reply());
        assertEquals("all clear", loaded.smallTalk(player).get(0).radioReply());

        loaded.removeEmployee(oldEmployee);
        assertTrue(loaded.dialogue(player, oldEmployee).isEmpty());
        assertEquals(1, loaded.dialogue(player, replacementWithSameName).size());
    }

    @Test
    void dispatchHistoryKeepsOnlyNewestBoundedTurns() {
        UUID player = UUID.randomUUID();
        LlmHistoryData data = new LlmHistoryData();
        for (int i = 0; i < 12; i++) data.appendDispatch(player, "command-" + i, "response-" + i);

        assertEquals(8, data.dispatch(player).size());
        assertEquals("command-4", data.dispatch(player).get(0).command());
        assertEquals("command-11", data.dispatch(player).get(7).command());
    }

    @Test
    void oversizedSingleTurnIsNotPersistedAsUnboundedHistory() {
        UUID player = UUID.randomUUID();
        LlmHistoryData data = new LlmHistoryData();
        data.appendDispatch(player, "x".repeat(20_000), "response");
        assertTrue(data.dispatch(player).isEmpty());
    }
}
