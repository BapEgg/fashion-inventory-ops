package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the low/base/high demand-rate half of {@code data/seed/mvp2}'s GS-01, GS-02,
 * GS-03 and GS-04, per {@code knowledge/business-rules.md} section 5.
 */
class DemandRateCalculationTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate LAUNCH_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void gs01FlatDemandGivesTheSameLowBaseHighRate() {
        DemandObservationWindow window = flatWindow(2);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);

        DemandRateCalculation result = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.STABLE_REPEAT, List.of());

        assertFalse(result.reviewRequired());
        assertEquals(4, result.eligibleWeeklyRates().size());
        BigDecimal expected = new BigDecimal("2.000000000000");
        assertEquals(expected, result.lowDemandRate());
        assertEquals(expected, result.baseDemandRate());
        assertEquals(expected, result.highDemandRate());
    }

    @Test
    void gs02EventDayIsExcludedFromItsWeekEvenThoughTheRateIsUnchanged() {
        // The GS-02 promotion (2026-09-29~2026-10-07) overlaps only the observation window's
        // last day, 2026-09-29 -- the final day of the fourth week -- so that week's eligible
        // day count must drop to 6, even though flat 2/day sales make the resulting rate the
        // same 2.0 as the other three weeks.
        DemandObservationWindow window = flatWindow(2);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", "STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        DemandRateCalculation withEvent = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.KNOWN_EVENT, List.of(event));
        DemandRateCalculation withoutEvent = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.STABLE_REPEAT, List.of());

        assertFalse(withEvent.reviewRequired());
        assertEquals(new BigDecimal("2.000000000000"), withEvent.baseDemandRate());
        assertEquals(withoutEvent.baseDemandRate(), withEvent.baseDemandRate());
    }

    @Test
    void upliftAppliesOnlyWhenTheEventOverlapsTheScenarioWindowAndHasCompleteUplift() {
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", "STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        var overlapping = event.upliftFor(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
        assertTrue(overlapping.isPresent());
        assertEquals(new BigDecimal("1.20"), overlapping.get().low());
        assertEquals(new BigDecimal("1.50"), overlapping.get().base());
        assertEquals(new BigDecimal("1.80"), overlapping.get().high());
        assertEquals(new BigDecimal("3.000000000000"),
                DemandRateCalculation.applyUplift(new BigDecimal("2.000000000000"), overlapping.get().base()));

        // Already elapsed by the time a future scenario window starts: must not reapply.
        var notOverlapping = event.upliftFor(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
        assertTrue(notOverlapping.isEmpty());
    }

    @Test
    void incompleteUpliftNeverApplies() {
        DemandEvent incomplete = new DemandEvent("EVENT-X", "STORE-A", "SKU-A",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7), null, new BigDecimal("1.5"), null);

        assertTrue(incomplete.upliftFor(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7)).isEmpty());
    }

    @Test
    void gs03SpikeEvidenceDayIsExcludedFromItsWeek() {
        int[] sales = new int[28];
        sales[18] = 20; // 2026-09-20, the GS-03 spike day
        DemandObservationWindow window = windowFor(sales);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);

        DemandRateCalculation result = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.UNEXPLAINED_SPIKE, List.of());

        assertEquals(LocalDate.of(2026, 9, 20), stats.spikeEvidenceDate());
        assertFalse(result.reviewRequired());
        assertEquals(4, result.eligibleWeeklyRates().size());
        assertEquals(new BigDecimal("0.000000000000"), result.baseDemandRate());
    }

    @Test
    void knownEventSignalDoesNotExcludeAStatisticallySpikeShapedDayFromTheBaseline() {
        // Same daily pattern as GS-03 (one 20-unit day, otherwise zero) produces a spike
        // candidate and a spikeEvidenceDate -- but when a separate, unrelated relevant event
        // makes the overall classified signal KNOWN_EVENT (section 3 checks KNOWN_EVENT before
        // UNEXPLAINED_SPIKE), that day is not the classified anomaly and must not be excluded
        // from the baseline the way it would be under a genuine UNEXPLAINED_SPIKE signal.
        int[] sales = new int[28];
        sales[18] = 20; // 2026-09-20
        DemandObservationWindow window = windowFor(sales);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);
        assertTrue(stats.spikeCandidate());
        assertEquals(LocalDate.of(2026, 9, 20), stats.spikeEvidenceDate());

        DemandEvent futureEvent = new DemandEvent("EVENT-FUTURE", "STORE-MVP2-RECEIVER-A", "SKU-X",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        DemandRateCalculation asKnownEvent = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.KNOWN_EVENT, List.of(futureEvent));
        DemandRateCalculation asUnexplainedSpike = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.UNEXPLAINED_SPIKE, List.of());

        BigDecimal zero = new BigDecimal("0.000000000000");
        BigDecimal spikeWeekRateIncluded = new BigDecimal("2.857142857143"); // 20/7, scale 12 HALF_UP
        assertEquals(List.of(zero, zero, zero, spikeWeekRateIncluded), asKnownEvent.eligibleWeeklyRates());
        assertEquals(List.of(zero, zero, zero, zero), asUnexplainedSpike.eligibleWeeklyRates());
    }

    @Test
    void gs04OnlyTwoValidWeeklyRatesSendsToReviewRequired() {
        int[] sales = new int[28];
        for (int i = 14; i < 28; i++) {
            sales[i] = 2;
        }
        DemandObservationWindow window = windowWithFirstDaysOutOfStock(14, sales);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);

        DemandRateCalculation result = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.INTERMITTENT, List.of());

        assertEquals(2, result.eligibleWeeklyRates().size());
        assertTrue(result.reviewRequired());
        assertNull(result.lowDemandRate());
        assertNull(result.baseDemandRate());
        assertNull(result.highDemandRate());
    }

    @Test
    void percentileInterpolationMatchesTheDocumentedFormula() {
        // Weekly totals 7, 14, 21, 28 -> rates 1, 2, 3, 4.
        int[] sales = new int[28];
        for (int week = 0; week < 4; week++) {
            for (int day = 0; day < 7; day++) {
                sales[week * 7 + day] = week + 1;
            }
        }
        DemandObservationWindow window = windowFor(sales);
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(window);
        assertFalse(stats.spikeCandidate());

        DemandRateCalculation result = DemandRateCalculation.calculate(
                window, stats, DemandSignalType.VARIABLE, List.of());

        assertFalse(result.reviewRequired());
        assertEquals(List.of(
                new BigDecimal("1.000000000000"),
                new BigDecimal("2.000000000000"),
                new BigDecimal("3.000000000000"),
                new BigDecimal("4.000000000000")
        ), result.eligibleWeeklyRates());
        // h=(4-1)*0.25=0.75, j=0, g=0.75 -> 1*0.25 + 2*0.75 = 1.75
        assertEquals(new BigDecimal("1.750000000000"), result.lowDemandRate());
        // h=1.5, j=1, g=0.5 -> 2*0.5 + 3*0.5 = 2.5
        assertEquals(new BigDecimal("2.500000000000"), result.baseDemandRate());
        // h=2.25, j=2, g=0.25 -> 3*0.75 + 4*0.25 = 3.25
        assertEquals(new BigDecimal("3.250000000000"), result.highDemandRate());
    }

    private static DemandObservationWindow flatWindow(int perDay) {
        int[] sales = new int[DemandAnalysisRules.OBSERVATION_WINDOW_DAYS];
        java.util.Arrays.fill(sales, perDay);
        return windowFor(sales);
    }

    private static DemandObservationWindow windowFor(int[] soldPerDay) {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int sold : soldPerDay) {
            days.add(sold == 0
                    ? DailyDemandObservation.of(date, 20, 0, 0, 0, 0)
                    : DailyDemandObservation.of(date, 20, 0, sold, sold, 1));
            date = date.plusDays(1);
        }
        return new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days);
    }

    private static DemandObservationWindow windowWithFirstDaysOutOfStock(int outOfStockDayCount, int[] soldPerDay) {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < soldPerDay.length; i++) {
            days.add(i < outOfStockDayCount
                    ? DailyDemandObservation.of(date, 0, 0, 0, 0, 0)
                    : DailyDemandObservation.of(date, 20, 0, soldPerDay[i], soldPerDay[i], 1));
            date = date.plusDays(1);
        }
        return new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days);
    }
}
