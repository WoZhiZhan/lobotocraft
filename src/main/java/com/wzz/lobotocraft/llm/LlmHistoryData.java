package com.wzz.lobotocraft.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class LlmHistoryData extends SavedData {
    private static final String ID = "lobotocraft_llm_history";
    private static final int MAX_DISPATCH_TURNS = 8;
    private static final int MAX_DISPATCH_CHARS = 16_000;
    private static final int MAX_DIALOGUE_TURNS = 8;
    private static final int MAX_DIALOGUE_CHARS = 8_000;
    private static final int MAX_SMALL_TALK_TURNS = 6;
    private static final int MAX_SMALL_TALK_CHARS = 4_000;

    private final Map<UUID, Deque<DispatchTurn>> dispatch = new LinkedHashMap<>();
    private final Map<DialogueKey, Deque<DialogueTurn>> dialogue = new LinkedHashMap<>();
    private final Map<UUID, Deque<SmallTalkTurn>> smallTalk = new LinkedHashMap<>();

    public static LlmHistoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                LlmHistoryData::load, LlmHistoryData::new, ID);
    }

    public List<DispatchTurn> dispatch(UUID playerId) {
        return copy(dispatch.get(playerId));
    }

    public List<DialogueTurn> dialogue(UUID playerId, UUID employeeId) {
        return copy(dialogue.get(new DialogueKey(playerId, employeeId)));
    }

    public List<SmallTalkTurn> smallTalk(UUID playerId) {
        return copy(smallTalk.get(playerId));
    }

    public void appendDispatch(UUID playerId, String command, String response) {
        Deque<DispatchTurn> turns = dispatch.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        turns.addLast(new DispatchTurn(safe(command), safe(response)));
        trim(turns, MAX_DISPATCH_TURNS, MAX_DISPATCH_CHARS, turn ->
                turn.command().length() + turn.response().length());
        setDirty();
    }

    public void appendDialogue(UUID playerId, UUID employeeId, String command,
                               String assignment, String reply) {
        Deque<DialogueTurn> turns = dialogue.computeIfAbsent(
                new DialogueKey(playerId, employeeId), ignored -> new ArrayDeque<>());
        turns.addLast(new DialogueTurn(safe(command), safe(assignment), safe(reply)));
        trim(turns, MAX_DIALOGUE_TURNS, MAX_DIALOGUE_CHARS, turn ->
                turn.command().length() + turn.assignment().length() + turn.reply().length());
        setDirty();
    }

    public void appendSmallTalk(UUID playerId, String playerMessage, String radioReply) {
        Deque<SmallTalkTurn> turns = smallTalk.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        turns.addLast(new SmallTalkTurn(safe(playerMessage), safe(radioReply)));
        trim(turns, MAX_SMALL_TALK_TURNS, MAX_SMALL_TALK_CHARS, turn ->
                turn.playerMessage().length() + turn.radioReply().length());
        setDirty();
    }

    public void removeEmployee(UUID employeeId) {
        if (employeeId == null) return;
        if (dialogue.keySet().removeIf(key -> key.employeeId().equals(employeeId))) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag dispatchList = new ListTag();
        dispatch.forEach((playerId, turns) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.put("Turns", saveDispatch(turns));
            dispatchList.add(entry);
        });
        tag.put("Dispatch", dispatchList);

        ListTag dialogueList = new ListTag();
        dialogue.forEach((key, turns) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", key.playerId());
            entry.putUUID("Employee", key.employeeId());
            entry.put("Turns", saveDialogue(turns));
            dialogueList.add(entry);
        });
        tag.put("Dialogue", dialogueList);

        ListTag smallTalkList = new ListTag();
        smallTalk.forEach((playerId, turns) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.put("Turns", saveSmallTalk(turns));
            smallTalkList.add(entry);
        });
        tag.put("SmallTalk", smallTalkList);
        return tag;
    }

    public static LlmHistoryData load(CompoundTag tag) {
        LlmHistoryData data = new LlmHistoryData();
        ListTag dispatchList = tag.getList("Dispatch", Tag.TAG_COMPOUND);
        for (int i = 0; i < dispatchList.size(); i++) {
            CompoundTag entry = dispatchList.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            Deque<DispatchTurn> turns = new ArrayDeque<>();
            ListTag turnList = entry.getList("Turns", Tag.TAG_COMPOUND);
            for (int j = 0; j < turnList.size(); j++) {
                CompoundTag turn = turnList.getCompound(j);
                turns.addLast(new DispatchTurn(turn.getString("Command"), turn.getString("Response")));
            }
            trim(turns, MAX_DISPATCH_TURNS, MAX_DISPATCH_CHARS, turn ->
                    turn.command().length() + turn.response().length());
            if (!turns.isEmpty()) data.dispatch.put(entry.getUUID("Player"), turns);
        }

        ListTag dialogueList = tag.getList("Dialogue", Tag.TAG_COMPOUND);
        for (int i = 0; i < dialogueList.size(); i++) {
            CompoundTag entry = dialogueList.getCompound(i);
            if (!entry.hasUUID("Player") || !entry.hasUUID("Employee")) continue;
            Deque<DialogueTurn> turns = new ArrayDeque<>();
            ListTag turnList = entry.getList("Turns", Tag.TAG_COMPOUND);
            for (int j = 0; j < turnList.size(); j++) {
                CompoundTag turn = turnList.getCompound(j);
                turns.addLast(new DialogueTurn(turn.getString("Command"),
                        turn.getString("Assignment"), turn.getString("Reply")));
            }
            trim(turns, MAX_DIALOGUE_TURNS, MAX_DIALOGUE_CHARS, turn ->
                    turn.command().length() + turn.assignment().length() + turn.reply().length());
            if (!turns.isEmpty()) data.dialogue.put(new DialogueKey(
                    entry.getUUID("Player"), entry.getUUID("Employee")), turns);
        }

        ListTag smallTalkList = tag.getList("SmallTalk", Tag.TAG_COMPOUND);
        for (int i = 0; i < smallTalkList.size(); i++) {
            CompoundTag entry = smallTalkList.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            Deque<SmallTalkTurn> turns = new ArrayDeque<>();
            ListTag turnList = entry.getList("Turns", Tag.TAG_COMPOUND);
            for (int j = 0; j < turnList.size(); j++) {
                CompoundTag turn = turnList.getCompound(j);
                turns.addLast(new SmallTalkTurn(turn.getString("PlayerMessage"), turn.getString("RadioReply")));
            }
            trim(turns, MAX_SMALL_TALK_TURNS, MAX_SMALL_TALK_CHARS, turn ->
                    turn.playerMessage().length() + turn.radioReply().length());
            if (!turns.isEmpty()) data.smallTalk.put(entry.getUUID("Player"), turns);
        }
        return data;
    }

    private static ListTag saveDispatch(Deque<DispatchTurn> turns) {
        ListTag list = new ListTag();
        for (DispatchTurn turn : turns) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Command", turn.command());
            entry.putString("Response", turn.response());
            list.add(entry);
        }
        return list;
    }

    private static ListTag saveDialogue(Deque<DialogueTurn> turns) {
        ListTag list = new ListTag();
        for (DialogueTurn turn : turns) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Command", turn.command());
            entry.putString("Assignment", turn.assignment());
            entry.putString("Reply", turn.reply());
            list.add(entry);
        }
        return list;
    }

    private static ListTag saveSmallTalk(Deque<SmallTalkTurn> turns) {
        ListTag list = new ListTag();
        for (SmallTalkTurn turn : turns) {
            CompoundTag entry = new CompoundTag();
            entry.putString("PlayerMessage", turn.playerMessage());
            entry.putString("RadioReply", turn.radioReply());
            list.add(entry);
        }
        return list;
    }

    private static <T> List<T> copy(Deque<T> source) {
        return source == null ? List.of() : List.copyOf(new ArrayList<>(source));
    }

    private static <T> void trim(Deque<T> turns, int maxTurns, int maxChars,
                                 java.util.function.ToIntFunction<T> size) {
        int chars = turns.stream().mapToInt(size).sum();
        while (turns.size() > maxTurns || chars > maxChars) {
            T removed = turns.removeFirst();
            chars -= size.applyAsInt(removed);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record DispatchTurn(String command, String response) {
    }

    public record DialogueTurn(String command, String assignment, String reply) {
    }

    public record SmallTalkTurn(String playerMessage, String radioReply) {
    }

    private record DialogueKey(UUID playerId, UUID employeeId) {
    }
}
