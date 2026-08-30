package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the observation-statistics half of {@code data/seed/mvp2}'s GS-01, GS-03 and
 * GS-04 (receiver store {@code STORE-MVP2-RECEIVER-A}, analysis date 2026-09-30, window
 * 2026-09-02~2026-09-29), per {@code knowledge/business-rules.md} sections 2-4. Demand signal
 * classification itself (section 3's full decision tree) and low/base/high rates (section 5)
 * are separate, later Java classes and are not asserted here.
 */
class DemandObservationStatisticsTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate LAUNCH_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void gs01StableRepeatDemandHasNoQualityFlagAndNoSpike() {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(DailyDemandObservation.of(date, 20, 0, 2, 2, 1));
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(28, stats.observableDayCount());
        assertFalse(stats.oosCensored());
        assertEquals(4, stats.activeWeekCount());
        assertEquals(new BigDecimal("1.000000"), stats.salesDayRatio());
        assertEquals(new BigDecimal("0.000000000000"), stats.weeklyCoefficientOfVariation());
        assertEquals(2, stats.maxDailySales());
        assertEquals(new BigDecimal("2.000000000000"), stats.medianDailySales());
        assertEquals(new BigDecimal("0.000000000000"), stats.madDailySales());
        assertEquals(56, stats.totalWindowSales());
        assertEquals(new BigDecimal("5.000000000000"), stats.spikeThreshold());
        assertFalse(stats.spikeCandidate());
        assertNull(stats.spikeEvidenceDate());
        assertFalse(stats.singleBulkTransaction());
        assertEquals(272, stats.launchDaysElapsed());
    }

    @Test
    void gs03UnexplainedSpikeAndSingleBulkTransactionAreFlagged() {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate spikeDate = LocalDate.of(2026, 9, 20);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(date.equals(spikeDate)
                    ? DailyDemandObservation.of(date, 20, 0, 20, 1, 20)
                    : DailyDemandObservation.of(date, 20, 0, 0, 0, 0));
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(28, stats.observableDayCount());
        assertFalse(stats.oosCensored());
        assertEquals(1, stats.activeWeekCount());
        assertEquals(new BigDecimal("0.000000000000"), stats.medianDailySales());
        assertEquals(new BigDecimal("0.000000000000"), stats.madDailySales());
        assertEquals(20, stats.maxDailySales());
        assertEquals(20, stats.totalWindowSales());
        assertEquals(new BigDecimal("5.000000000000"), stats.spikeThreshold());
        assertTrue(stats.spikeCandidate());
        assertEquals(spikeDate, stats.spikeEvidenceDate());
        assertEquals(20, stats.maxTransactionQuantityInWindow());
        assertTrue(stats.singleBulkTransaction());
    }

    @Test
    void gs04OosCensoredDaysAreExcludedFromDemandEvidence() {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(i < 14
                    ? DailyDemandObservation.of(date, 0, 0, 0, 0, 0)
                    : DailyDemandObservation.of(date, 20, 0, 2, 2, 1));
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        // Exactly the minimum observable-day boundary (business-rules.md section 1): 14 is
        // sufficient, not DATA_INSUFFICIENT.
        assertEquals(14, stats.observableDayCount());
        assertEquals(14, stats.oosCensoredDayCount());
        assertTrue(stats.oosCensored());
        assertEquals(2, stats.activeWeekCount());
        assertEquals(new BigDecimal("1.000000"), stats.salesDayRatio());
        assertEquals(new BigDecimal("1.000000000000"), stats.weeklyCoefficientOfVariation());
        assertEquals(2, stats.maxDailySales());
        assertEquals(new BigDecimal("2.000000000000"), stats.medianDailySales());
        assertEquals(new BigDecimal("0.000000000000"), stats.madDailySales());
        // Zero-sale OOS-censored days are not treated as demand evidence, but their recorded
        // zero sales still do not inflate totalWindowSales either: 14 observable days * 2.
        assertEquals(28, stats.totalWindowSales());
        assertFalse(stats.spikeCandidate());
    }

    @Test
    void allDaysOutOfStockLeavesStatisticsUndefinedNotZero() {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(DailyDemandObservation.of(date, 0, 0, 0, 0, 0));
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(0, stats.observableDayCount());
        assertEquals(28, stats.oosCensoredDayCount());
        assertTrue(stats.oosCensored());
        assertEquals(0, stats.activeWeekCount());
        assertNull(stats.salesDayRatio());
        assertNull(stats.weeklyCoefficientOfVariation());
        assertNull(stats.maxDailySales());
        assertNull(stats.medianDailySales());
        assertNull(stats.madDailySales());
        assertEquals(0, stats.totalWindowSales());
        assertNull(stats.spikeThreshold());
        assertFalse(stats.spikeCandidate());
        assertNull(stats.spikeEvidenceDate());
        assertFalse(stats.singleBulkTransaction());
    }

    @Test
    void evenObservableDayCountMedianIsAverageOfTwoMiddleValues() {
        // 14 observable days: sold quantities 1,1,...,1 (x7), 3,3,...,3 (x7) -> sorted median is
        // the average of the 7th and 8th values (1 and 3): 2.0 exactly.
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            if (i < 14) {
                days.add(DailyDemandObservation.of(date, 0, 0, 0, 0, 0));
            } else {
                int sold = (i < 21) ? 1 : 3;
                days.add(DailyDemandObservation.of(date, 20, 0, sold, sold, 1));
            }
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(14, stats.observableDayCount());
        assertEquals(new BigDecimal("2.000000000000"), stats.medianDailySales());
    }

    @Test
    void explicitOutOfStockFlagExcludesDayFromEvidenceDespitePositiveOnHandQuantity() {
        // Same as GS-01 (28 days, 2 sold/day) but one day is explicitly flagged out of stock
        // (e.g. damaged/held stock) even though the ledger still shows 20 on hand. Section 2
        // requires honoring that explicit input rather than re-deriving observability purely
        // from the quantity math. A stocked-out day always records zero sales (enforced by
        // DailyDemandObservation), so it is a genuine OOS_CENSORED day, not an invalid one.
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            if (i == 10) {
                OffsetDateTime snapshotAt = date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
                days.add(new DailyDemandObservation(date, 20, 0, 0, 0, 0, true, snapshotAt));
            } else {
                days.add(DailyDemandObservation.of(date, 20, 0, 2, 2, 1));
            }
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(27, stats.observableDayCount());
        assertEquals(1, stats.oosCensoredDayCount());
        assertTrue(stats.oosCensored());
        assertEquals(0, stats.invalidSnapshotDayCount());
        // The flagged day contributes zero sales, so the window total is 27 days * 2, not 28.
        assertEquals(54, stats.totalWindowSales());
    }

    @Test
    void mismatchedSnapshotDateIsExcludedButDoesNotTriggerOosCensoredFlag() {
        // A stale/mis-dated snapshot_at (e.g. a reconciliation lag) must not be treated as a
        // trustworthy reference for that calendar day, but it is a data-trust problem, not a
        // stockout: it must not set the OOS_CENSORED quality flag, and its own recorded sales
        // must not leak into totalWindowSales either.
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            if (i == 5) {
                OffsetDateTime staleSnapshotAt =
                        date.minusDays(3).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
                days.add(new DailyDemandObservation(date, 20, 0, 2, 2, 1, false, staleSnapshotAt));
            } else {
                days.add(DailyDemandObservation.of(date, 20, 0, 2, 2, 1));
            }
            date = date.plusDays(1);
        }

        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));

        assertEquals(27, stats.observableDayCount());
        assertEquals(0, stats.oosCensoredDayCount());
        assertFalse(stats.oosCensored());
        assertEquals(1, stats.invalidSnapshotDayCount());
        // The invalid day's own recorded 2 units must not inflate the window total (27 * 2).
        assertEquals(54, stats.totalWindowSales());
    }
}
