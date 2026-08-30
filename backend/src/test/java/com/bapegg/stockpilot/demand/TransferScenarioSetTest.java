package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the scenario-quantity half of {@code data/seed/mvp2}'s GS-01 and GS-02, per
 * {@code knowledge/business-rules.md} section 8, including both stores' before/after available
 * quantity, coverage days, risk code, timing/basis evidence and constraint warnings.
 */
class TransferScenarioSetTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);

    private static DemandRateCalculation flatRate(String rate) {
        BigDecimal value = new BigDecimal(rate);
        return new DemandRateCalculation(List.of(value), value, value, value, false);
    }

    @Test
    void gs01ThreeAutomaticScenariosProtectBothStores() {
        // Receiver STORE-MVP2-RECEIVER-A: on_hand 4, an open transfer of 2 already inbound from
        // DONOR-A -> projectedReceiverBeforeDemand = 4 + 2 = 6. Flat 2/day demand: low=base=high=2.0.
        InventoryProjection receiverProjection = InventoryProjection.calculate(4, 0, 0, 2, 0, 0, 0);
        DemandRateCalculation rates = flatRate("2.000000000000");
        // Donor STORE-MVP2-DONOR-A: on_hand 80, 2 already committed to the same open transfer
        // -> projectedDonorAtDispatch = 80 - 2 = 78. Flat 1/day demand: low=base=high=1.0.
        InventoryProjection donorProjection = InventoryProjection.calculate(80, 0, 0, 0, 2, 0, 0);
        DemandRateCalculation donorRates = flatRate("1.000000000000");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 1, 100,
                donorProjection, donorRates, 14, 1, 2, route);

        assertFalse(result.comparisonOnly());
        assertEquals(4, result.scenarios().size());

        TransferScenarioResult noAction = result.scenarios().get(0);
        assertEquals(TransferScenarioType.NO_ACTION, noAction.scenarioType());
        assertEquals(0, noAction.scenarioQuantity());
        assertTrue(noAction.feasible());
        assertEquals(6, noAction.receiverBeforeAvailable());
        assertEquals(6, noAction.receiverAfterAvailable());
        // NO_ACTION's own quantity is 0, but its coverage/risk still use the real BASE rate
        // (2.0/day here, no KNOWN_EVENT uplift) -- not a literal 0 standing in for "no demand".
        assertEquals(new BigDecimal("2.000000000000"), noAction.demandRate());
        assertEquals(new BigDecimal("3.000000000000"), noAction.receiverBeforeCoverageDays());
        assertEquals(new BigDecimal("3.000000000000"), noAction.receiverAfterCoverageDays());
        assertEquals(InventoryExceptionType.STOCKOUT_RISK, noAction.receiverRiskCode());
        assertEquals(78, noAction.donorBeforeAvailable());
        assertEquals(78, noAction.donorAfterAvailable());
        assertEquals(new BigDecimal("78.000000000000"), noAction.donorBeforeCoverageDays());
        assertEquals(InventoryExceptionType.OVERSTOCK, noAction.donorRiskCode());
        assertNull(noAction.warningSummary());
        assertEquals(1, noAction.leadTimeDays());
        assertEquals(LocalDate.of(2026, 10, 1), noAction.expectedArrivalDate());
        // Explicit confirmed-inbound/open-transfer evidence, not just a boolean flag.
        assertEquals(0, noAction.receiverInboundArrivingBeforeTransfer());
        assertEquals(2, noAction.receiverOpenTransferInbound());
        assertEquals(0, noAction.receiverOpenTransferOutbound());
        assertEquals(0, noAction.donorInboundArrivingBeforeDispatch());
        assertEquals(2, noAction.donorOpenTransferOutbound());
        assertEquals(0, noAction.donorAlreadyApprovedDraftQuantity());

        // targetQuantity = ceil(2.0 * (1 + 7)) + 1 = 17; receiverNeed = 17 - 6 = 11.
        // donorTransferable = 78 - (ceil(1.0*14) + 1 + 2) = 78 - 17 = 61.
        // rawQuantity = min(11, 61, 50, 94) = 11; both stores stay protected afterward.
        for (TransferScenarioResult scenario : result.scenarios().subList(1, 4)) {
            assertEquals(11, scenario.rawQuantity());
            assertEquals(11, scenario.scenarioQuantity());
            assertTrue(scenario.feasible());
            assertNull(scenario.warningSummary());
            assertEquals(1, scenario.leadTimeDays());
            assertEquals(LocalDate.of(2026, 10, 1), scenario.expectedArrivalDate());
            assertEquals(2, scenario.receiverOpenTransferInbound());
            assertEquals(2, scenario.donorOpenTransferOutbound());

            assertEquals(6, scenario.receiverBeforeAvailable());
            assertEquals(17, scenario.receiverAfterAvailable());
            assertEquals(new BigDecimal("3.000000000000"), scenario.receiverBeforeCoverageDays());
            assertEquals(new BigDecimal("8.500000000000"), scenario.receiverAfterCoverageDays());
            // 17 == the target quantity exactly: not short of coverage anymore.
            assertEquals(InventoryExceptionType.NORMAL, scenario.receiverRiskCode());

            assertEquals(78, scenario.donorBeforeAvailable());
            assertEquals(67, scenario.donorAfterAvailable());
            assertEquals(new BigDecimal("78.000000000000"), scenario.donorBeforeCoverageDays());
            assertEquals(new BigDecimal("67.000000000000"), scenario.donorAfterCoverageDays());
            // 67 - 17 protected = 50 still transferable: the donor remains a healthy surplus store.
            assertEquals(InventoryExceptionType.OVERSTOCK, scenario.donorRiskCode());
        }
    }

    @Test
    void gs02KnownEventUpliftIsAppliedToEachScenarioIncludingNoAction() {
        // Same flat 2/day baseline as GS-01 (receiver 6 available, donor 78 available, route
        // lead 1 day, target coverage 7 days), but the signal is KNOWN_EVENT with the real
        // GS-02 promotion (2026-09-29~2026-10-07, uplift 1.20/1.50/1.80). The scenario's own
        // arrival-through-target-coverage window is [2026-10-01, 2026-10-08], which the event
        // overlaps, so every auto scenario's rate -- and NO_ACTION's own risk/coverage rate --
        // must be the baseline multiplied by its own uplift factor, scale 12, before sizing.
        InventoryProjection receiverProjection = InventoryProjection.calculate(4, 0, 0, 2, 0, 0, 0);
        DemandRateCalculation rates = flatRate("2.000000000000");
        InventoryProjection donorProjection = InventoryProjection.calculate(80, 0, 0, 0, 2, 0, 0);
        DemandRateCalculation donorRates = flatRate("1.000000000000");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", "STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.KNOWN_EVENT, DemandConfidence.MEDIUM, rates, ANALYSIS_DATE, event,
                receiverProjection, 7, 1, 100,
                donorProjection, donorRates, 14, 1, 2, route);

        TransferScenarioResult noAction = result.scenarios().get(0);
        TransferScenarioResult conservative = result.scenarios().get(1);
        TransferScenarioResult base = result.scenarios().get(2);
        TransferScenarioResult aggressive = result.scenarios().get(3);

        // NO_ACTION uses the uplifted BASE rate (2.0 * 1.50 = 3.0), not the raw 2.0.
        assertEquals(new BigDecimal("3.000000000000"), noAction.demandRate());
        assertEquals(new BigDecimal("2.000000000000"), noAction.receiverBeforeCoverageDays());

        assertEquals(new BigDecimal("2.400000000000"), conservative.demandRate());
        assertEquals(new BigDecimal("3.000000000000"), base.demandRate());
        assertEquals(new BigDecimal("3.600000000000"), aggressive.demandRate());

        // targetQuantity(2.4) = ceil(2.4*8)+1 = 20+1 = 21; receiverNeed = 21-6 = 15.
        assertEquals(15, conservative.scenarioQuantity());
        // targetQuantity(3.0) = ceil(3.0*8)+1 = 24+1 = 25; receiverNeed = 25-6 = 19.
        assertEquals(19, base.scenarioQuantity());
        // targetQuantity(3.6) = ceil(3.6*8)+1 = 29+1 = 30; receiverNeed = 30-6 = 24.
        assertEquals(24, aggressive.scenarioQuantity());
    }

    @Test
    void noUpliftWhenEventDoesNotOverlapTheScenarioWindow() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(4, 0, 0, 2, 0, 0, 0);
        DemandRateCalculation rates = flatRate("2.000000000000");
        InventoryProjection donorProjection = InventoryProjection.calculate(80, 0, 0, 0, 2, 0, 0);
        DemandRateCalculation donorRates = flatRate("1.000000000000");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);
        // Entirely before the scenario's [2026-10-01, 2026-10-08] window.
        DemandEvent pastEvent = new DemandEvent("EVENT-PAST", "STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.KNOWN_EVENT, DemandConfidence.MEDIUM, rates, ANALYSIS_DATE, pastEvent,
                receiverProjection, 7, 1, 100,
                donorProjection, donorRates, 14, 1, 2, route);

        assertEquals(new BigDecimal("2.000000000000"), result.scenarios().get(0).demandRate());
        assertEquals(new BigDecimal("2.000000000000"), result.scenarios().get(2).demandRate());
        assertEquals(11, result.scenarios().get(2).scenarioQuantity());
    }

    @Test
    void noUpliftWhenSignalIsNotKnownEvent() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(4, 0, 0, 2, 0, 0, 0);
        DemandRateCalculation rates = flatRate("2.000000000000");
        InventoryProjection donorProjection = InventoryProjection.calculate(80, 0, 0, 0, 2, 0, 0);
        DemandRateCalculation donorRates = flatRate("1.000000000000");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", "STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 7),
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, event,
                receiverProjection, 7, 1, 100,
                donorProjection, donorRates, 14, 1, 2, route);

        assertEquals(new BigDecimal("2.000000000000"), result.scenarios().get(2).demandRate());
    }

    @Test
    void differingLowBaseHighRatesProduceDifferentScenarioQuantities() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = new DemandRateCalculation(
                List.of(new BigDecimal("1.750000000000"), new BigDecimal("2.500000000000"), new BigDecimal("3.250000000000")),
                new BigDecimal("1.750000000000"), new BigDecimal("2.500000000000"), new BigDecimal("3.250000000000"),
                false);
        DemandRateCalculation donorRates = flatRate("0.000000000001");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 0, 1000,
                donorProjection, donorRates, 0, 0, 0, route);

        // targetQuantity = ceil(rate * 8): 1.75*8=14, 2.5*8=20, 3.25*8=26 -- all exact integers.
        assertEquals(14, result.scenarios().get(1).scenarioQuantity());
        assertEquals(20, result.scenarios().get(2).scenarioQuantity());
        assertEquals(26, result.scenarios().get(3).scenarioQuantity());
    }

    @Test
    void packageMultipleFloorsTheRawQuantityDown() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("3.000000000000");
        DemandRateCalculation donorRates = flatRate("0.000000000001");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 5, 100);

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 0, 1000,
                donorProjection, donorRates, 0, 0, 0, route);

        // targetQuantity = ceil(3.0 * 8) = 24; floored to the nearest multiple of 5 -> 20.
        TransferScenarioResult base = result.scenarios().get(2);
        assertEquals(24, base.rawQuantity());
        assertEquals(20, base.scenarioQuantity());
        assertTrue(base.feasible());
    }

    @Test
    void belowRouteMinimumIsInfeasibleWithZeroQuantityAndAWarningNamingTheFlooredQuantity() {
        // raw 17, package multiple 10 -> floored 10, which is below the route's minimum of 15.
        // The warning must explain "10 < 15", not the pre-floor raw 17.
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1.000000000000");
        DemandRateCalculation donorRates = flatRate("0.000000000001");
        TransferRoute route = new TransferRoute(true, false, 1, 15, 10, 100);

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 16, 0, 1000,
                donorProjection, donorRates, 0, 0, 0, route);

        // targetQuantity = ceil(1.0 * (1 + 16)) = 17.
        TransferScenarioResult base = result.scenarios().get(2);
        assertEquals(17, base.rawQuantity());
        assertEquals(0, base.scenarioQuantity());
        assertFalse(base.feasible());
        assertTrue(base.warningSummary().contains("10"));
        assertTrue(base.warningSummary().contains("15"));
        assertFalse(base.warningSummary().contains("17 is below"));
        // The receiver stays exactly where it started: nothing was actually shipped.
        assertEquals(base.receiverBeforeAvailable(), base.receiverAfterAvailable());
    }

    @Test
    void donorSupplyCanBeTheBindingConstraint() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        // donorProtected = ceil(1*14) + 1 + 2 = 17; available 20 leaves only 3 transferable.
        InventoryProjection donorProjection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("5.000000000000");
        DemandRateCalculation donorRates = flatRate("1.000000000000");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 0, 1000,
                donorProjection, donorRates, 14, 1, 2, route);

        // receiverNeed = ceil(5*8) = 40, far above the donor's transferable 3.
        TransferScenarioResult base = result.scenarios().get(2);
        assertEquals(3, base.rawQuantity());
        assertEquals(3, base.scenarioQuantity());
    }

    @Test
    void variableSignalMarksComparisonOnlyButStillComputesAllFourScenariosEvenWithLowConfidence() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = new DemandRateCalculation(
                List.of(new BigDecimal("1.750000000000"), new BigDecimal("2.500000000000"), new BigDecimal("3.250000000000")),
                new BigDecimal("1.750000000000"), new BigDecimal("2.500000000000"), new BigDecimal("3.250000000000"),
                false);
        DemandRateCalculation donorRates = flatRate("0.000000000001");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        // VARIABLE's whole purpose is showing comparison scenarios even when confidence is not
        // HIGH -- unlike every other signal type, LOW confidence must not block it.
        TransferScenarioSet result = TransferScenarioSet.calculate(
                DemandSignalType.VARIABLE, DemandConfidence.LOW, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 0, 1000,
                donorProjection, donorRates, 0, 0, 0, route);

        assertTrue(result.comparisonOnly());
        assertEquals(14, result.scenarios().get(1).scenarioQuantity());
        assertEquals(20, result.scenarios().get(2).scenarioQuantity());
        assertEquals(26, result.scenarios().get(3).scenarioQuantity());
    }

    @Test
    void nullRouteIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");

        assertThrows(IllegalArgumentException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, null));
    }

    @Test
    void inactiveRouteIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute inactiveRoute = new TransferRoute(false, false, 1, 1, 1, 100);

        assertThrows(IllegalArgumentException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, inactiveRoute));
    }

    @Test
    void nonPositiveReceiverMaximumCapacityIsRejected() {
        // V6's ck_sp_policy_values requires maximum_capacity > 0; a non-positive value must not
        // silently produce a zero-quantity result indistinguishable from a real NO_ACTION.
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalArgumentException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 0,
                projection, rates, 14, 1, 2, route));
        assertThrows(IllegalArgumentException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                projection, 7, 1, -1,
                projection, rates, 14, 1, 2, route));
    }

    @Test
    void reviewRequiredReceiverRatesAreRejected() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation reviewRequiredRates =
                new DemandRateCalculation(List.of(BigDecimal.ZERO), null, null, null, true);
        DemandRateCalculation donorRates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, reviewRequiredRates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, donorRates, 14, 1, 2, route));
    }

    @Test
    void reviewRequiredDonorRatesAreAlsoRejected() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        DemandRateCalculation reviewRequiredDonorRates =
                new DemandRateCalculation(List.of(BigDecimal.ZERO), null, null, null, true);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, reviewRequiredDonorRates, 14, 1, 2, route));
    }

    @Test
    void invalidReceiverProjectionIsRejected() {
        // openTransferOutbound (10) exceeds available (5): isInputInvalid() is true.
        InventoryProjection invalidReceiver = InventoryProjection.calculate(5, 0, 0, 0, 10, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                invalidReceiver, 7, 1, 100,
                donorProjection, rates, 14, 1, 2, route));
    }

    @Test
    void invalidDonorProjectionIsRejected() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        InventoryProjection invalidDonor = InventoryProjection.calculate(5, 0, 0, 0, 10, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, rates, ANALYSIS_DATE, null,
                receiverProjection, 7, 1, 100,
                invalidDonor, rates, 14, 1, 2, route));
    }

    @Test
    void dataInsufficientSignalIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.DATA_INSUFFICIENT, DemandConfidence.NONE, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, route));
    }

    @Test
    void unexplainedSpikeSignalIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.UNEXPLAINED_SPIKE, DemandConfidence.LOW, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, route));
    }

    @Test
    void intermittentSignalIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.INTERMITTENT, DemandConfidence.LOW, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, route));
    }

    @Test
    void incompleteUpliftKnownEventIsRejected() {
        // DemandSignalClassification downgrades an incomplete-uplift KNOWN_EVENT to LOW
        // confidence; that LOW confidence alone must block automatic scenarios here too.
        InventoryProjection projection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.KNOWN_EVENT, DemandConfidence.LOW, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, route));
    }

    @Test
    void qualityFlaggedStableRepeatIsRejected() {
        // A STABLE_REPEAT signal with a quality flag (e.g. OOS_CENSORED) is downgraded to LOW
        // confidence by DemandSignalClassification; re-deriving a signal-type allowlist here
        // (instead of checking confidence directly) would miss exactly this case, per the same
        // lesson already learned on InventoryExceptionClassification.
        InventoryProjection projection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rates = flatRate("1");
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 100);

        assertThrows(IllegalStateException.class, () -> TransferScenarioSet.calculate(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.LOW, rates, ANALYSIS_DATE, null,
                projection, 7, 1, 100,
                projection, rates, 14, 1, 2, route));
    }
}
