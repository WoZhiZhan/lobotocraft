package com.lobotocraft.facility.day;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DayVoteState {
    public enum Vote { APPROVE, REJECT }
    public enum Outcome { PENDING, PASSED, FAILED }

    private final int day;
    private final long deadline;
    private final double approvalRatio;
    private final Set<UUID> eligible;
    private final Map<UUID, Vote> votes = new LinkedHashMap<>();

    public DayVoteState(int day, long deadline, double approvalRatio, Set<UUID> eligible) {
        if (eligible == null || eligible.isEmpty()) throw new IllegalArgumentException("eligible voters cannot be empty");
        this.day = Math.max(1, day);
        this.deadline = deadline;
        this.approvalRatio = Math.max(0.0D, Math.min(1.0D, approvalRatio));
        this.eligible = Collections.unmodifiableSet(new LinkedHashSet<>(eligible));
    }

    public boolean cast(UUID voter, Vote vote) {
        if (!eligible.contains(voter) || votes.containsKey(voter) || vote == null) return false;
        votes.put(voter, vote);
        return true;
    }

    public Outcome outcome(long gameTime) {
        int required = requiredApprovals();
        int rejects = rejectCount();
        if (eligible.size() - rejects < required) return Outcome.FAILED;
        if (votes.size() == eligible.size()) return approvalCount() >= required ? Outcome.PASSED : Outcome.FAILED;
        if (gameTime >= deadline) return eligible.size() - rejects >= required ? Outcome.PASSED : Outcome.FAILED;
        return Outcome.PENDING;
    }

    public int day() { return day; }
    public long deadline() { return deadline; }
    public Set<UUID> eligible() { return eligible; }
    public Vote voteOf(UUID voter) { return votes.get(voter); }
    public int approvalCount() { return (int) votes.values().stream().filter(vote -> vote == Vote.APPROVE).count(); }
    public int rejectCount() { return (int) votes.values().stream().filter(vote -> vote == Vote.REJECT).count(); }
    public int requiredApprovals() {
        return Math.max(0, Math.min(eligible.size(), (int) Math.ceil(eligible.size() * approvalRatio - 1.0E-9D)));
    }
}
