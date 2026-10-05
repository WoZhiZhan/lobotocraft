package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.EmployeeStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkAttributeLevelsTest {
    @Test
    void fourWorkTypesReadTheirCorrespondingSingleAttributeNotOverallLevel() {
        var stats = new EmployeeStats();
        stats.setFortitude(20);
        stats.setPrudence(30);
        stats.setTemperance(45);
        stats.setJustice(65);
        var levels = WorkAttributeLevels.from(stats);
        assertEquals(3, stats.getEmployeeLevel());
        assertEquals(1, levels.levelFor(WorkType.INSTINCT));
        assertEquals(2, levels.levelFor(WorkType.INSIGHT));
        assertEquals(3, levels.levelFor(WorkType.ATTACHMENT));
        assertEquals(4, levels.levelFor(WorkType.REPRESSION));
        for (var type : WorkType.values())
            assertEquals(levels.levelFor(type) - 1, levels.columnFor(type));
    }

    @Test
    void independentSnapshotCannotTurnEveryStatIntoOverallV() {
        var stats = new EmployeeStats();
        stats.setFortitude(85); stats.setPrudence(85); stats.setTemperance(20); stats.setJustice(85);
        assertEquals(5, stats.getEmployeeLevel());
        var snapshot = WorkAttributeLevels.from(stats);
        assertEquals(1, snapshot.levelFor(WorkType.ATTACHMENT));
        stats.setTemperance(100);
        assertEquals(1, snapshot.levelFor(WorkType.ATTACHMENT));
        assertEquals(5, WorkAttributeLevels.from(stats).levelFor(WorkType.ATTACHMENT));
    }

    @Test
    void absentStatsDefaultToIAndColumnsStayWithinIToV() {
        assertEquals(WorkAttributeLevels.LOWEST, WorkAttributeLevels.from(null));
        assertEquals(new WorkAttributeLevels(1, 1, 5, 5),
                new WorkAttributeLevels(-1, 0, 6, Integer.MAX_VALUE));
    }
}
