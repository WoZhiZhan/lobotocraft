package com.lobotocraft.facility.day;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DayVoteStateTest {
    @Test
    void abstentionCountsAsApprovalOnlyAtResolutionTime() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DayVoteState vote = new DayVoteState(4, 200L, 1.0D, Set.of(first, second));

        assertTrue(vote.cast(first, DayVoteState.Vote.APPROVE));
        assertEquals(DayVoteState.Outcome.PENDING, vote.outcome(199L));
        assertEquals(DayVoteState.Outcome.PASSED, vote.outcome(200L));
    }

    @Test
    void unanimousVoteFailsAsSoonAsOneFrozenVoterRejects() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DayVoteState vote = new DayVoteState(1, 100L, 1.0D, Set.of(first, second));

        assertTrue(vote.cast(second, DayVoteState.Vote.REJECT));
        assertEquals(DayVoteState.Outcome.FAILED, vote.outcome(1L));
    }

    @Test
    void ratioThresholdUsesFrozenEligibleSet() {
        LinkedHashSet<UUID> voters = new LinkedHashSet<>();
        for (int i = 0; i < 5; i++) voters.add(UUID.randomUUID());
        DayVoteState vote = new DayVoteState(2, 50L, 0.6D, voters);
        var iterator = voters.iterator();
        vote.cast(iterator.next(), DayVoteState.Vote.REJECT);
        vote.cast(iterator.next(), DayVoteState.Vote.REJECT);

        assertEquals(3, vote.requiredApprovals());
        assertEquals(DayVoteState.Outcome.PENDING, vote.outcome(49L));
        assertEquals(DayVoteState.Outcome.PASSED, vote.outcome(50L));
        assertThrows(UnsupportedOperationException.class,
                () -> vote.eligible().add(UUID.randomUUID()));
    }

    @Test
    void duplicateAndOutsiderVotesCannotReplaceAChoice() {
        UUID voter = UUID.randomUUID();
        DayVoteState vote = new DayVoteState(1, 20L, 1.0D, Set.of(voter));

        assertTrue(vote.cast(voter, DayVoteState.Vote.APPROVE));
        assertFalse(vote.cast(voter, DayVoteState.Vote.REJECT));
        assertFalse(vote.cast(UUID.randomUUID(), DayVoteState.Vote.APPROVE));
        assertEquals(1, vote.approvalCount());
        assertEquals(0, vote.rejectCount());
        assertEquals(DayVoteState.Outcome.PASSED, vote.outcome(0L));
    }

    @Test
    void enoughRejectionsMakesPartialRatioImpossibleEarly() {
        Set<UUID> voters = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        DayVoteState vote = new DayVoteState(1, 100L, 0.8D, voters);
        var iterator = voters.iterator();
        vote.cast(iterator.next(), DayVoteState.Vote.REJECT);
        assertEquals(DayVoteState.Outcome.PENDING, vote.outcome(1L));
        vote.cast(iterator.next(), DayVoteState.Vote.REJECT);
        assertEquals(DayVoteState.Outcome.FAILED, vote.outcome(1L));
    }
}
