package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectiveReceiverBaseRateTest {

    private static final TransferRoute ROUTE = new TransferRoute(true, false, 4, 1, 1, 100);
    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final BigDecimal BASELINE = new BigDecimal("2.000000000000");

    @Test
    void appliesTheUpliftBaseWhenTheEventOverlapsTheRouteArrivalWindow() {
        // arrival = 2026-10-04 (lead 4), target coverage end = 2026-10-04 + 7 = 2026-10-11.
        DemandEvent event = new DemandEvent("EVT-1", "STORE-R", "SKU-1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        BigDecimal effective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, event, ANALYSIS_DATE, ROUTE, 7);

        assertEquals(0, new BigDecimal("3.000000000000").compareTo(effective));
    }

    @Test
    void fallsBackToTheBaselineRateWhenTheSignalIsNotKnownEvent() {
        DemandEvent event = new DemandEvent("EVT-1", "STORE-R", "SKU-1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        BigDecimal effective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.STABLE_REPEAT, event, ANALYSIS_DATE, ROUTE, 7);

        assertEquals(0, BASELINE.compareTo(effective));
    }

    @Test
    void fallsBackToTheBaselineRateWhenThereIsNoRepresentativeEvent() {
        BigDecimal effective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, null, ANALYSIS_DATE, ROUTE, 7);

        assertEquals(0, BASELINE.compareTo(effective));
    }

    @Test
    void fallsBackToTheBaselineRateWhenTheEventDoesNotOverlapThisRoutesArrivalWindow() {
        // arrival = 2026-10-04, target coverage end = 2026-10-11; event ends well before that.
        DemandEvent event = new DemandEvent("EVT-1", "STORE-R", "SKU-1",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));

        BigDecimal effective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, event, ANALYSIS_DATE, ROUTE, 7);

        assertEquals(0, BASELINE.compareTo(effective));
    }

    @Test
    void fallsBackToTheBaselineRateWhenTheEventHasIncompleteUplift() {
        DemandEvent event = new DemandEvent("EVT-1", "STORE-R", "SKU-1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20),
                null, new BigDecimal("1.5"), null);

        BigDecimal effective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, event, ANALYSIS_DATE, ROUTE, 7);

        assertEquals(0, BASELINE.compareTo(effective));
    }

    @Test
    void differentRoutesCanGetDifferentEffectiveRatesForTheSameEvent() {
        // A slower route's arrival window can miss an event that a faster route's window catches.
        DemandEvent event = new DemandEvent("EVT-1", "STORE-R", "SKU-1",
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5),
                new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.8"));
        TransferRoute fastRoute = new TransferRoute(true, false, 3, 1, 1, 100); // arrival 2026-10-03
        TransferRoute slowRoute = new TransferRoute(true, false, 30, 1, 1, 100); // arrival 2026-10-30

        BigDecimal fastEffective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, event, ANALYSIS_DATE, fastRoute, 0);
        BigDecimal slowEffective = EffectiveReceiverBaseRate.calculate(
                BASELINE, DemandSignalType.KNOWN_EVENT, event, ANALYSIS_DATE, slowRoute, 0);

        assertEquals(0, new BigDecimal("3.000000000000").compareTo(fastEffective));
        assertEquals(0, BASELINE.compareTo(slowEffective));
    }
}
