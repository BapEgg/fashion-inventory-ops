package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanHorizonTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);

    @Test
    void endsAtTheLongestActiveRouteLeadTimePlusTargetCoverage() {
        // GS-02's two active routes into STORE-MVP2-RECEIVER-A: lead time 1 and 10 days,
        // target coverage 7 days -> max(1, 10) + 7 = 17.
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));

        assertEquals(LocalDate.of(2026, 10, 17), horizon.endDate());
    }

    @Test
    void fallsBackToTheFlatSevenDayHorizonWithNoActiveRoute() {
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of());

        assertEquals(LocalDate.of(2026, 10, 7), horizon.endDate());
    }

    @Test
    void nullRouteListAlsoFallsBackToTheFlatHorizon() {
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, null);

        assertEquals(LocalDate.of(2026, 10, 7), horizon.endDate());
    }

    @Test
    void overlapsChecksInclusiveBounds() {
        PlanHorizon horizon = new PlanHorizon(ANALYSIS_DATE, LocalDate.of(2026, 10, 7));

        assertTrue(horizon.overlaps(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30)));
        assertTrue(horizon.overlaps(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 20)));
        assertFalse(horizon.overlaps(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)));
        assertFalse(horizon.overlaps(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20)));
    }

    @Test
    void endDateBeforeAnalysisDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlanHorizon(ANALYSIS_DATE, ANALYSIS_DATE.minusDays(1)));
    }

    @Test
    void negativeTargetCoverageDaysIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlanHorizon.of(ANALYSIS_DATE, -1, List.of(1)));
    }

    @Test
    void negativeTargetCoverageDaysIsRejectedEvenWithNoActiveRoute() {
        assertThrows(IllegalArgumentException.class, () -> PlanHorizon.of(ANALYSIS_DATE, -1, List.of()));
    }

    @Test
    void negativeRouteLeadTimeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, -5)));
    }

    @Test
    void nullRouteLeadTimeEntryIsRejected() {
        List<Integer> withNull = new java.util.ArrayList<>(List.of(1));
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> PlanHorizon.of(ANALYSIS_DATE, 7, withNull));
    }
}
