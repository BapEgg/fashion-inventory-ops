package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DailyDemandObservation;
import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandEvent;
import com.bapegg.stockpilot.demand.DemandObservationWindow;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferRoute;
import com.bapegg.stockpilot.rebalance.InboundStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link Mvp2CalculationOrchestrator} boundaries that the
 * {@code MVP-2-GS-V1} golden scenario data does not exercise: {@code REQUESTED}-only pending
 * conflict, {@code MISSING_INBOUND}/{@code STALE_INVENTORY} quality flags, and deterministic
 * ordering. Every {@link Mvp2InputGraph} here is built directly in memory -- no Spring/DB.
 */
class Mvp2CalculationOrchestratorTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final ZoneOffset SEOUL = ZoneOffset.of("+09:00");
    private static final String RECEIVER = "STORE-R";
    private static final String DONOR = "STORE-D";
    private static final String SKU = "SKU-1";
    private static final Mvp2Policy POLICY = new Mvp2Policy(1, 2, 100, 7, 14);
    private static final TransferRoute ACTIVE_ROUTE = new TransferRoute(true, false, 1, 1, 1, 50);

    @Test
    void requestedOpenTransferOnTheSameLaneBlocksAsPendingConflict() {
        Mvp2InputGraph graph = graphWithOpenTransfer(OpenTransferStatus.REQUESTED);

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2CandidateResult candidate = onlyCandidate(result);
        assertTrue(candidate.rejectionReasons().contains(TransferCandidateRejectionReason.PENDING_TRANSFER_CONFLICT));
    }

    @Test
    void approvedOpenTransferOnTheSameLaneDoesNotBlockAsPendingConflict() {
        Mvp2InputGraph graph = graphWithOpenTransfer(OpenTransferStatus.APPROVED);

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2CandidateResult candidate = onlyCandidate(result);
        assertFalse(candidate.rejectionReasons().contains(TransferCandidateRejectionReason.PENDING_TRANSFER_CONFLICT));
    }

    private Mvp2InputGraph graphWithOpenTransfer(OpenTransferStatus status) {
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2OpenTransferRow> openTransfers = List.of(new Mvp2OpenTransferRow(DONOR, RECEIVER, SKU, 3, status));
        return buildGraph(List.of(receiver, donor), List.of(), List.of(), openTransfers, routes());
    }

    @Test
    void anIncompleteInboundRowSetsTheMissingInboundQualityFlag() {
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2InboundRow> inbound = List.of(new Mvp2InboundRow(RECEIVER, SKU, null, null, InboundStatus.PLANNED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), inbound, List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertTrue(metric.qualityFlags().contains(com.bapegg.stockpilot.demand.MetricQualityFlag.MISSING_INBOUND));
    }

    @Test
    void aCompleteInboundRowDoesNotSetTheMissingInboundQualityFlag() {
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        OffsetDateTime eta = ANALYSIS_DATE.plusDays(3).atStartOfDay(SEOUL).toOffsetDateTime();
        List<Mvp2InboundRow> inbound = List.of(new Mvp2InboundRow(RECEIVER, SKU, 5, eta, InboundStatus.CONFIRMED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), inbound, List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertFalse(metric.qualityFlags().contains(com.bapegg.stockpilot.demand.MetricQualityFlag.MISSING_INBOUND));
    }

    @Test
    void aCurrentSnapshotOlderThanTwentyFourHoursBeforeTheReferenceInstantSetsStaleInventory() {
        // analysisReferenceAt = 2026-10-01 00:00 Asia/Seoul; two days earlier is unambiguously stale.
        OffsetDateTime staleSnapshotAt = ANALYSIS_DATE.minusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        Mvp2Anchor receiver = anchorWithSnapshotAt(RECEIVER, "OWNER-A", 4, 0, 2, staleSnapshotAt);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertTrue(metric.qualityFlags().contains(com.bapegg.stockpilot.demand.MetricQualityFlag.STALE_INVENTORY));
    }

    @Test
    void aCurrentSnapshotWithinTwentyFourHoursOfTheReferenceInstantDoesNotSetStaleInventory() {
        OffsetDateTime freshSnapshotAt = ANALYSIS_DATE.atStartOfDay(SEOUL).toOffsetDateTime();
        Mvp2Anchor receiver = anchorWithSnapshotAt(RECEIVER, "OWNER-A", 4, 0, 2, freshSnapshotAt);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertFalse(metric.qualityFlags().contains(com.bapegg.stockpilot.demand.MetricQualityFlag.STALE_INVENTORY));
    }

    @Test
    void metricsAndCandidatesAreOrderedDeterministicallyByStoreAndSkuRegardlessOfInputOrder() {
        Mvp2Anchor receiverB = anchor("STORE-R-B", "SKU-B", "OWNER-A", 4, 0, 2);
        Mvp2Anchor donorB = anchor("STORE-D-B", "SKU-B", "OWNER-A", 80, 0, 1);
        Mvp2Anchor receiverA = anchor("STORE-R-A", "SKU-A", "OWNER-A", 4, 0, 2);
        Mvp2Anchor donorA = anchor("STORE-D-A", "SKU-A", "OWNER-A", 80, 0, 1);
        // Deliberately reverse/scrambled insertion order.
        List<Mvp2Route> reverseRoutes = List.of(
                new Mvp2Route(2L, "STORE-D-B", "STORE-R-B", ACTIVE_ROUTE),
                new Mvp2Route(1L, "STORE-D-A", "STORE-R-A", ACTIVE_ROUTE));
        Mvp2InputGraph graph = buildGraph(
                List.of(receiverB, donorB, receiverA, donorA), List.of(), List.of(), List.of(), reverseRoutes);

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        List<String> metricKeys = result.metrics().stream().map(m -> m.storeId() + "/" + m.skuId()).toList();
        List<String> sortedKeys = metricKeys.stream().sorted().toList();
        assertEquals(sortedKeys, metricKeys, "Metrics must already be in (storeId, skuId) ascending order.");
        assertEquals(metricKeys, result.metricsByStoreSku().keySet().stream()
                .map(key -> key.storeId() + "/" + key.skuId())
                .toList(), "The identified metric map must preserve the same deterministic key order.");

        List<String> candidateReceivers = result.candidates().stream().map(Mvp2CandidateResult::receiverStoreId).toList();
        assertEquals(List.of("STORE-R-A", "STORE-R-B"), candidateReceivers);
        assertEquals(
                List.of("STORE-R-A/SKU-A", "STORE-R-B/SKU-B"),
                result.candidatesByReceiver().keySet().stream()
                        .map(key -> key.storeId() + "/" + key.skuId())
                        .toList(),
                "The identified candidate map must preserve receiver/store key order.");
        assertThrows(UnsupportedOperationException.class,
                () -> result.candidatesByReceiver().get(new Mvp2StoreSkuKey("STORE-R-A", "SKU-A")).clear());
    }

    @Test
    void staleInventoryDowngradesConfidenceToLowBlocksTheCandidateAndSendsTheMetricToReviewRequired() {
        // Otherwise a plain STABLE_REPEAT/HIGH-confidence shortage (onHand=4 against a rate=2
        // target of ~17) that would normally produce a candidate.
        OffsetDateTime staleSnapshotAt = ANALYSIS_DATE.minusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        Mvp2Anchor receiver = anchorWithSnapshotAt(RECEIVER, "OWNER-A", 4, 0, 2, staleSnapshotAt);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertEquals(DemandConfidence.LOW, metric.signal().confidence());
        assertEquals(InventoryExceptionType.REVIEW_REQUIRED, metric.exception().exceptionType());
        assertTrue(candidatesFor(result, RECEIVER, SKU).isEmpty(),
                "A quality-flag-downgraded LOW confidence must block candidate generation just like a naturally LOW one.");
    }

    @Test
    void missingInboundDowngradesConfidenceToLowAndBlocksTheCandidateToo() {
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2InboundRow> incompleteInbound = List.of(new Mvp2InboundRow(RECEIVER, SKU, null, null, InboundStatus.PLANNED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), incompleteInbound, List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertEquals(DemandConfidence.LOW, metric.signal().confidence());
        assertTrue(candidatesFor(result, RECEIVER, SKU).isEmpty());
    }

    @Test
    void aReceiverAlreadyToppedUpByAnApprovedInboundOpenTransferIsNotEvaluatedAsShort() {
        // onHand=4 alone looks short against the ~17 target, but a 20-unit APPROVED inbound
        // open transfer (already-committed movement, not a per-lane hypothesis) closes it.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2OpenTransferRow> openTransfers =
                List.of(new Mvp2OpenTransferRow(DONOR, RECEIVER, SKU, 20, OpenTransferStatus.APPROVED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), openTransfers, routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        assertTrue(candidatesFor(result, RECEIVER, SKU).isEmpty(),
                "An approved inbound transfer that already covers the target must exclude the receiver from evaluation.");
    }

    @Test
    void aReceiverNewlyShortenedByAnApprovedOutboundOpenTransferIsEvaluated() {
        // onHand=30 alone comfortably covers the ~17 target, but a 20-unit APPROVED outbound
        // open transfer (this store as donor in some other lane) drops it below target.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 30, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2OpenTransferRow> openTransfers =
                List.of(new Mvp2OpenTransferRow(RECEIVER, "STORE-OTHER", SKU, 20, OpenTransferStatus.APPROVED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), openTransfers, routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        assertFalse(candidatesFor(result, RECEIVER, SKU).isEmpty(),
                "An approved outbound transfer must be able to newly qualify a receiver that looked comfortable on currentAvailable alone.");
    }

    @Test
    void anInvalidNegativeShortageCheckProjectionExcludesTheReceiverEntirely() {
        // outbound (20) far exceeds onHand (5): the shortage-check projection's donor-shaped
        // reading goes negative, an input inconsistency rather than a legitimate shortage signal.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 5, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2OpenTransferRow> openTransfers =
                List.of(new Mvp2OpenTransferRow(RECEIVER, "STORE-OTHER", SKU, 20, OpenTransferStatus.APPROVED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), openTransfers, routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        assertTrue(candidatesFor(result, RECEIVER, SKU).isEmpty(),
                "An invalid (negative) shortage-check projection must exclude the receiver, not be treated as extreme shortage.");
    }

    @Test
    void projectedReceiverAtArrivalSubtractsLeadTimeDemandRatherThanReusingThePreArrivalSnapshot() {
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2CandidateResult candidate = onlyCandidate(result);
        // projectedReceiverBeforeDemand = 4 (onHand, no other evidence); route leadTimeDays=1,
        // effective rate=2 (STABLE_REPEAT, no event) => arrival = 4 - ceil(2*1) = 2, not 4.
        assertEquals(2L, candidate.projectedReceiverAtArrival());
    }

    @Test
    void metricQualityFlagsIterateInEnumDeclarationOrderRegardlessOfWhichWasDetectedFirst() {
        // MISSING_INBOUND is detected after STALE_INVENTORY in the orchestrator's own code, but
        // MetricQualityFlag declares OOS_CENSORED, STALE_INVENTORY, MISSING_INBOUND,
        // INCOMPLETE_EVENT_DATA -- iteration must follow that declared order, not detection order.
        OffsetDateTime staleSnapshotAt = ANALYSIS_DATE.minusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        Mvp2Anchor receiver = anchorWithSnapshotAt(RECEIVER, "OWNER-A", 4, 0, 2, staleSnapshotAt);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2InboundRow> incompleteInbound = List.of(new Mvp2InboundRow(RECEIVER, SKU, null, null, InboundStatus.PLANNED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), incompleteInbound, List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertEquals(
                List.of(MetricQualityFlag.STALE_INVENTORY, MetricQualityFlag.MISSING_INBOUND),
                List.copyOf(metric.qualityFlags()));
        assertThrows(UnsupportedOperationException.class,
                () -> metric.qualityFlags().add(MetricQualityFlag.OOS_CENSORED));
        assertThrows(UnsupportedOperationException.class,
                () -> result.metricsByStoreSku().put(new Mvp2StoreSkuKey("X", "Y"), metric));
    }

    @Test
    void anOpenTransferAggregateBeyondTheIntegerRangeThrowsInputContractViolation() {
        // Each individual quantity fits an int; only their sum overflows.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2OpenTransferRow> openTransfers = List.of(
                new Mvp2OpenTransferRow(DONOR, RECEIVER, SKU, 1_500_000_000, OpenTransferStatus.APPROVED),
                new Mvp2OpenTransferRow(DONOR, RECEIVER, SKU, 1_500_000_000, OpenTransferStatus.IN_TRANSIT));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), openTransfers, routes());

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> Mvp2CalculationOrchestrator.calculate(graph));
        assertTrue(exception.getMessage().contains("does not fit the domain's 32-bit integer range"));
    }

    @Test
    void aConfirmedInboundAggregateBeyondTheIntegerRangeThrowsInputContractViolation() {
        OffsetDateTime eta = ANALYSIS_DATE.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        List<Mvp2InboundRow> inbound = List.of(
                new Mvp2InboundRow(RECEIVER, SKU, 1_500_000_000, eta, InboundStatus.CONFIRMED),
                new Mvp2InboundRow(RECEIVER, SKU, 1_500_000_000, eta, InboundStatus.CONFIRMED));
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), inbound, List.of(), routes());

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> Mvp2CalculationOrchestrator.calculate(graph));
        assertTrue(exception.getMessage().contains("does not fit the domain's 32-bit integer range"));
    }

    @Test
    void expectedShortageQuantityIsThePositiveBaseTargetGapForAShortReceiver() {
        // rate=2 (STABLE_REPEAT), route leadTimeDays=1, targetCoverageDays=7, displayMinimum=1
        // => target = ceil(2*8)+1 = 17; projectedReceiverBeforeDemand = onHand = 4 => 17-4=13.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertEquals(13L, metric.expectedShortageQuantity());
    }

    @Test
    void expectedShortageQuantityIsZeroWhenTheAnchorAlreadyMeetsItsOwnBaseTarget() {
        // Donor has no active route of its own (activeRouteLeadTimes empty for DONOR as receiver),
        // so earliestArrivalLeadTimeDays falls back to 7: target = ceil(1*14)+1 = 15, well below
        // its own onHand=80 -- the gap must clamp to zero, not go negative.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 2);
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(DONOR, SKU));
        assertEquals(0L, metric.expectedShortageQuantity());
    }

    @Test
    void expectedShortageQuantityIsNullWhenNoBaseRateExists() {
        // Every day of the 28-day window is stocked out (onHand=0), so no week has an observable
        // day at all: fewer than 3 valid weekly rates leaves DemandRateCalculation.baseDemandRate
        // null, which must propagate straight through as a null expectedShortageQuantity.
        Mvp2Anchor receiver = anchorWithWindow(RECEIVER, "OWNER-A", 4, 0, oosWindow());
        Mvp2Anchor donor = anchor(DONOR, "OWNER-A", 80, 0, 1);
        Mvp2InputGraph graph = buildGraph(List.of(receiver, donor), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertNull(metric.rates().baseDemandRate());
        assertNull(metric.expectedShortageQuantity());
    }

    @Test
    void expectedShortageQuantityHandlesAValueBeyondTheIntegerRangeWithoutNarrowingOverflow() {
        // A flat 300,000,000/day rate drives the target (ceil(300_000_000*8)+1 = 2,400,000,001)
        // past Integer.MAX_VALUE (2,147,483,647) -- the gap against onHand=4 must still be the
        // exact long value, not a wrapped or truncated one. No donor anchor is present (the route
        // row alone still supplies the receiver's leadTimeDays=1) so this huge rate never reaches
        // candidate evaluation's own int-shortage plumbing -- that path is exercised at ordinary
        // magnitudes elsewhere; this test isolates expectedShortageQuantity's own long arithmetic.
        Mvp2Anchor receiver = anchor(RECEIVER, "OWNER-A", 4, 0, 300_000_000);
        Mvp2InputGraph graph = buildGraph(List.of(receiver), List.of(), List.of(), List.of(), routes());

        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, SKU));
        assertEquals(2_399_999_997L, metric.expectedShortageQuantity());
    }

    private static List<Mvp2CandidateResult> candidatesFor(Mvp2CalculationResult result, String receiverStoreId, String skuId) {
        return result.candidatesByReceiver().getOrDefault(new Mvp2StoreSkuKey(receiverStoreId, skuId), List.of());
    }

    private static Mvp2CandidateResult onlyCandidate(Mvp2CalculationResult result) {
        assertEquals(1, result.candidates().size());
        return result.candidates().get(0);
    }

    private static List<Mvp2Route> routes() {
        return List.of(new Mvp2Route(1L, DONOR, RECEIVER, ACTIVE_ROUTE));
    }

    private static Mvp2Anchor anchor(String storeId, String ownerCode, int onHand, int reserved, int dailySales) {
        return anchor(storeId, SKU, ownerCode, onHand, reserved, dailySales);
    }

    private static Mvp2Anchor anchor(
            String storeId, String skuId, String ownerCode, int onHand, int reserved, int dailySales) {
        return anchorWithSnapshotAt(
                storeId, skuId, ownerCode, onHand, reserved, dailySales,
                ANALYSIS_DATE.atStartOfDay(SEOUL).toOffsetDateTime());
    }

    private static Mvp2Anchor anchorWithSnapshotAt(
            String storeId, String ownerCode, int onHand, int reserved, int dailySales, OffsetDateTime snapshotAt) {
        return anchorWithSnapshotAt(storeId, SKU, ownerCode, onHand, reserved, dailySales, snapshotAt);
    }

    private static Mvp2Anchor anchorWithSnapshotAt(
            String storeId, String skuId, String ownerCode, int onHand, int reserved, int dailySales,
            OffsetDateTime snapshotAt) {
        return new Mvp2Anchor(
                storeId, skuId, ownerCode, onHand, reserved, snapshotAt, (onHand - reserved) <= 0,
                flatWindow(dailySales), POLICY);
    }

    private static Mvp2Anchor anchorWithWindow(
            String storeId, String ownerCode, int onHand, int reserved, DemandObservationWindow window) {
        return new Mvp2Anchor(
                storeId, SKU, ownerCode, onHand, reserved, ANALYSIS_DATE.atStartOfDay(SEOUL).toOffsetDateTime(),
                (onHand - reserved) <= 0, window, POLICY);
    }

    private static DemandObservationWindow oosWindow() {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate start = ANALYSIS_DATE.minusDays(28);
        for (int i = 0; i < 28; i++) {
            days.add(DailyDemandObservation.of(start.plusDays(i), 0, 0, 0, 0, 0));
        }
        return new DemandObservationWindow(ANALYSIS_DATE, LocalDate.of(2020, 1, 1), days);
    }

    private static DemandObservationWindow flatWindow(int dailySales) {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate start = ANALYSIS_DATE.minusDays(28);
        for (int i = 0; i < 28; i++) {
            LocalDate date = start.plusDays(i);
            days.add(DailyDemandObservation.of(date, 100, 0, dailySales, dailySales > 0 ? 1 : 0, dailySales > 0 ? dailySales : 0));
        }
        return new DemandObservationWindow(ANALYSIS_DATE, LocalDate.of(2020, 1, 1), days);
    }

    private static Mvp2InputGraph buildGraph(
            List<Mvp2Anchor> anchors, List<DemandEvent> events, List<Mvp2InboundRow> inbound,
            List<Mvp2OpenTransferRow> openTransfers, List<Mvp2Route> routeRows) {
        Map<Mvp2StoreSkuKey, List<DemandEvent>> eventsByStoreSku = groupBy(events, e -> new Mvp2StoreSkuKey(e.storeId(), e.skuId()));
        Map<Mvp2StoreSkuKey, List<Mvp2InboundRow>> inboundByStoreSku = groupBy(inbound, r -> new Mvp2StoreSkuKey(r.storeId(), r.skuId()));
        Map<Mvp2LaneKey, List<Mvp2OpenTransferRow>> openTransfersByLane =
                groupBy(openTransfers, r -> new Mvp2LaneKey(r.donorStoreId(), r.receiverStoreId(), r.skuId()));
        Map<Mvp2StorePairKey, List<Mvp2Route>> routesByStorePair =
                groupBy(routeRows, r -> new Mvp2StorePairKey(r.donorStoreId(), r.receiverStoreId()));
        return new Mvp2InputGraph(
                ANALYSIS_DATE, "TEST-VERSION", anchors, events, inbound, openTransfers, routeRows,
                Map.of(), eventsByStoreSku, inboundByStoreSku, openTransfersByLane, routesByStorePair);
    }

    private static <T, K> Map<K, List<T>> groupBy(List<T> items, java.util.function.Function<T, K> keyFn) {
        Map<K, List<T>> result = new java.util.LinkedHashMap<>();
        for (T item : items) {
            result.computeIfAbsent(keyFn.apply(item), k -> new ArrayList<>()).add(item);
        }
        return result;
    }
}
