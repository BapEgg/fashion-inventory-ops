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
 * Reproduces the signal-classification half of {@code data/seed/mvp2}'s GS-01 and GS-02
 * (receiver store {@code STORE-MVP2-RECEIVER-A}, analysis date 2026-09-30), per
 * {@code knowledge/business-rules.md} sections 3-4, plus the remaining branches
 * (`DATA_INSUFFICIENT`, `UNEXPLAINED_SPIKE`, `INTERMITTENT`, `VARIABLE`) with hand-derived
 * synthetic inputs. low/base/high demand rates (section 5) are a separate, later class.
 */
class DemandSignalClassificationTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate LAUNCH_DATE = LocalDate.of(2026, 1, 1);
    private static final String RECEIVER_STORE_ID = "STORE-MVP2-RECEIVER-A";

    @Test
    void gs01StableRepeatDemandWithNoEventsIsHighConfidence() {
        DemandObservationStatistics stats = statisticsFor(flatSales(2), LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-MVP2-GS01-STABLE", stats, horizon, List.of());

        assertEquals(DemandSignalType.STABLE_REPEAT, result.signalType());
        assertEquals(DemandConfidence.HIGH, result.confidence());
        assertNull(result.relevantEvent());
        assertFalse(result.incompleteEventData());
    }

    @Test
    void gs02KnownEventWithCompleteUpliftIsMediumConfidence() {
        // Identical underlying demand pattern to GS-01 -- the only difference is the active
        // promotion event, which must still win over the otherwise-STABLE_REPEAT pattern
        // because section 3 checks KNOWN_EVENT before STABLE_REPEAT.
        DemandObservationStatistics stats = statisticsFor(flatSales(2), LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", RECEIVER_STORE_ID, "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-MVP2-GS02-EVENT", stats, horizon, List.of(event));

        assertEquals(DemandSignalType.KNOWN_EVENT, result.signalType());
        assertEquals(DemandConfidence.MEDIUM, result.confidence());
        assertEquals(event, result.relevantEvent());
        assertFalse(result.incompleteEventData());
    }

    @Test
    void eventForADifferentSkuIsIgnored() {
        DemandObservationStatistics stats = statisticsFor(flatSales(2), LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));
        DemandEvent unrelatedEvent = new DemandEvent("EVENT-OTHER", RECEIVER_STORE_ID, "SKU-OTHER",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-MVP2-GS01-STABLE", stats, horizon, List.of(unrelatedEvent));

        assertEquals(DemandSignalType.STABLE_REPEAT, result.signalType());
    }

    @Test
    void incompleteUpliftKnownEventIsDowngradedToLowConfidence() {
        DemandObservationStatistics stats = statisticsFor(flatSales(2), LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));
        DemandEvent incompleteEvent = new DemandEvent("EVENT-INCOMPLETE", RECEIVER_STORE_ID, "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7), null, new BigDecimal("1.5"), null);

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-MVP2-GS02-EVENT", stats, horizon, List.of(incompleteEvent));

        assertEquals(DemandSignalType.KNOWN_EVENT, result.signalType());
        assertEquals(DemandConfidence.LOW, result.confidence());
        assertTrue(result.incompleteEventData());
    }

    @Test
    void insufficientLaunchDaysIsDataInsufficientRegardlessOfOtherInputs() {
        // Only 5 days since launch (< the 14-day minimum), even though the observation
        // pattern and an event would otherwise both suggest a different signal. The primary
        // signal and confidence follow DATA_INSUFFICIENT/NONE regardless, but the relevant
        // event and its completeness are still preserved -- quality-flag evidence is stored
        // independently of the single primary signal (section 3), not discarded here.
        LocalDate recentLaunch = ANALYSIS_DATE.minusDays(5);
        DemandObservationStatistics stats = statisticsFor(flatSales(2), recentLaunch);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));
        DemandEvent event = new DemandEvent("EVENT-X", RECEIVER_STORE_ID, "SKU-X",
                ANALYSIS_DATE, ANALYSIS_DATE, new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-X", stats, horizon, List.of(event));

        assertEquals(DemandSignalType.DATA_INSUFFICIENT, result.signalType());
        assertEquals(DemandConfidence.NONE, result.confidence());
        assertEquals(event, result.relevantEvent());
        assertFalse(result.incompleteEventData());
    }

    @Test
    void insufficientObservableDaysStillPreservesIncompleteEventDataFlag() {
        // Fewer than 14 observable days (all 28 days out of stock), with a relevant but
        // uplift-incomplete event -- DATA_INSUFFICIENT/NONE still wins the primary signal, but
        // incompleteEventData must not be silently reset to false.
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(DailyDemandObservation.of(date, 0, 0, 0, 0, 0));
            date = date.plusDays(1);
        }
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(
                new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));
        DemandEvent incompleteEvent = new DemandEvent("EVENT-INCOMPLETE", RECEIVER_STORE_ID, "SKU-X",
                ANALYSIS_DATE, ANALYSIS_DATE, null, new BigDecimal("1.5"), null);

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-X", stats, horizon, List.of(incompleteEvent));

        assertEquals(0, stats.observableDayCount());
        assertEquals(DemandSignalType.DATA_INSUFFICIENT, result.signalType());
        assertEquals(DemandConfidence.NONE, result.confidence());
        assertEquals(incompleteEvent, result.relevantEvent());
        assertTrue(result.incompleteEventData());
    }

    @Test
    void unexplainedSpikeWithNoEventIsLowConfidence() {
        // GS-03's pattern: 28 days of zero sales except one day of 20.
        int[] sales = new int[28];
        sales[18] = 20; // 2026-09-20
        DemandObservationStatistics stats = statisticsFor(sales, LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-MVP2-GS03-SPIKE", stats, horizon, List.of());

        assertEquals(DemandSignalType.UNEXPLAINED_SPIKE, result.signalType());
        assertEquals(DemandConfidence.LOW, result.confidence());
    }

    @Test
    void sparseSalesWithLowSalesDayRatioIsIntermittent() {
        int[] sales = new int[28];
        sales[0] = 1;
        sales[1] = 1;
        sales[2] = 1;
        // 3 of 28 observable days have sales: ratio 0.107 < 0.25, and only one active week.
        DemandObservationStatistics stats = statisticsFor(sales, LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-SPARSE", stats, horizon, List.of());

        assertEquals(DemandSignalType.INTERMITTENT, result.signalType());
        assertEquals(DemandConfidence.LOW, result.confidence());
    }

    @Test
    void frequentButHighlyVariableWeeksIsVariableWithMediumConfidence() {
        // Active every week (so not INTERMITTENT) and no single-day spike, but the four
        // weekly totals (3, 3, 3, 15) vary too much for STABLE_REPEAT's CV <= 0.35.
        int[] sales = new int[28];
        sales[0] = 1;
        sales[1] = 1;
        sales[2] = 1;
        sales[7] = 1;
        sales[8] = 1;
        sales[9] = 1;
        sales[14] = 1;
        sales[15] = 1;
        sales[16] = 1;
        sales[21] = 2;
        sales[22] = 2;
        sales[23] = 2;
        sales[24] = 2;
        sales[25] = 2;
        sales[26] = 2;
        sales[27] = 3;
        DemandObservationStatistics stats = statisticsFor(sales, LAUNCH_DATE);
        PlanHorizon horizon = PlanHorizon.of(ANALYSIS_DATE, 7, List.of(1, 10));

        DemandSignalClassification result = DemandSignalClassification.classify(
                RECEIVER_STORE_ID, "SKU-VARIABLE", stats, horizon, List.of());

        assertEquals(4, stats.activeWeekCount());
        assertFalse(stats.spikeCandidate());
        assertEquals(DemandSignalType.VARIABLE, result.signalType());
        assertEquals(DemandConfidence.MEDIUM, result.confidence());
    }

    private static int[] flatSales(int perDay) {
        int[] sales = new int[DemandAnalysisRules.OBSERVATION_WINDOW_DAYS];
        java.util.Arrays.fill(sales, perDay);
        return sales;
    }

    private static DemandObservationStatistics statisticsFor(int[] soldPerDay, LocalDate launchDate) {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int sold : soldPerDay) {
            days.add(sold == 0
                    ? DailyDemandObservation.of(date, 20, 0, 0, 0, 0)
                    : DailyDemandObservation.of(date, 20, 0, sold, sold, 1));
            date = date.plusDays(1);
        }
        return DemandObservationStatistics.calculate(new DemandObservationWindow(ANALYSIS_DATE, launchDate, days));
    }
}
