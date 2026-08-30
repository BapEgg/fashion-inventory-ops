package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.ApprovalBasisRecalculation;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandEvent;
import com.bapegg.stockpilot.demand.DemandObservationStatistics;
import com.bapegg.stockpilot.demand.DemandRateCalculation;
import com.bapegg.stockpilot.demand.DemandSignalClassification;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.EffectiveReceiverBaseRate;
import com.bapegg.stockpilot.demand.InventoryExceptionClassification;
import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.PlanHorizon;
import com.bapegg.stockpilot.demand.RepresentativeEventSelection;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferRoute;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.demand.TransferScenarioSet;
import com.bapegg.stockpilot.demand.TransferScenarioType;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.InboundStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connects an {@link Mvp2InputGraph} to the existing pure {@code demand} calculation rules, per
 * {@code current-task.md}'s calculation order (business-rules.md sections 2-9) and
 * {@code data-model.md}'s Phase 3 mapping. Entirely pure: no JPA entity, repository,
 * {@code JdbcTemplate} or Spring Batch type appears here or in the {@link Mvp2CalculationResult}
 * it returns, and it executes zero SQL of its own -- every input comes from the graph the
 * {@link Mvp2InputAdapter} already loaded.
 */
public final class Mvp2CalculationOrchestrator {

    private static final ZoneId ASSUMPTION_TIMEZONE = ZoneId.of("Asia/Seoul");

    private Mvp2CalculationOrchestrator() {
    }

    public static Mvp2CalculationResult calculate(Mvp2InputGraph graph) {
        LocalDate analysisDate = graph.analysisDate();
        String inputSnapshotVersion = graph.inputSnapshotVersion();

        List<Mvp2Anchor> sortedAnchors = graph.anchors().stream()
                .sorted(Comparator.comparing(Mvp2Anchor::storeId).thenComparing(Mvp2Anchor::skuId))
                .toList();

        Map<Mvp2StoreSkuKey, AnchorEvidence> evidenceByKey = new LinkedHashMap<>();
        for (Mvp2Anchor anchor : sortedAnchors) {
            evidenceByKey.put(storeSkuKey(anchor), buildAnchorEvidence(anchor, graph, analysisDate));
        }

        Map<String, List<Mvp2Anchor>> anchorsBySku = new LinkedHashMap<>();
        for (Mvp2Anchor anchor : sortedAnchors) {
            anchorsBySku.computeIfAbsent(anchor.skuId(), k -> new ArrayList<>()).add(anchor);
        }

        List<Mvp2CandidateResult> candidates = new ArrayList<>();
        Set<Mvp2StoreSkuKey> receiversWithEligibleCandidate = new HashSet<>();

        for (Mvp2Anchor receiverAnchor : sortedAnchors) {
            AnchorEvidence receiverEvidence = evidenceByKey.get(storeSkuKey(receiverAnchor));
            if (cannotAutoQuantify(receiverEvidence.signal.confidence(), receiverEvidence.signal.signalType())
                    || !hasMetricLevelBaseShortage(receiverAnchor, receiverEvidence)) {
                continue;
            }
            List<Mvp2Anchor> sameSkuAnchors = anchorsBySku.getOrDefault(receiverAnchor.skuId(), List.of()).stream()
                    .sorted(Comparator.comparing(Mvp2Anchor::storeId))
                    .toList();
            for (Mvp2Anchor donorAnchor : sameSkuAnchors) {
                if (donorAnchor.storeId().equals(receiverAnchor.storeId())) {
                    continue;
                }
                AnchorEvidence donorEvidence = evidenceByKey.get(storeSkuKey(donorAnchor));
                if (donorEvidence.rates.reviewRequired() || donorEvidence.rates.highDemandRate() == null
                        || donorEvidence.projection.isInputInvalid()) {
                    continue;
                }
                Mvp2CandidateResult candidate = evaluateCandidate(
                        graph, analysisDate, receiverAnchor, receiverEvidence, donorAnchor, donorEvidence);
                candidates.add(candidate);
                if (candidate.candidateStatus() == CandidateStatus.ELIGIBLE) {
                    receiversWithEligibleCandidate.add(storeSkuKey(receiverAnchor));
                }
            }
        }

        List<Mvp2MetricResult> metrics = new ArrayList<>();
        for (Mvp2Anchor anchor : sortedAnchors) {
            AnchorEvidence evidence = evidenceByKey.get(storeSkuKey(anchor));
            boolean hasActionableCandidate = receiversWithEligibleCandidate.contains(storeSkuKey(anchor));
            InventoryExceptionClassification finalException = hasActionableCandidate
                    ? InventoryExceptionClassification.classify(
                            evidence.projection, evidence.signal.confidence(), evidence.rates,
                            evidence.earliestArrivalLeadTimeDays, anchor.policy().targetCoverageDays(),
                            anchor.policy().retainedDays(), anchor.policy().displayMinimum(),
                            anchor.policy().safetyStock(), true)
                    : evidence.provisionalException;
            Long expectedShortageQuantity = expectedShortageQuantity(
                    evidence.projection, evidence.rates.baseDemandRate(), evidence.earliestArrivalLeadTimeDays, anchor.policy());
            metrics.add(new Mvp2MetricResult(
                    anchor.storeId(), anchor.skuId(), evidence.stats, evidence.signal, evidence.rates,
                    evidence.projection, evidence.earliestArrivalLeadTimeDays, finalException, expectedShortageQuantity,
                    evidence.qualityFlags, DemandAnalysisRules.RULE_VERSION));
        }

        Map<Mvp2StoreSkuKey, Mvp2MetricResult> metricsByStoreSku = new LinkedHashMap<>();
        for (Mvp2MetricResult metric : metrics) {
            metricsByStoreSku.put(new Mvp2StoreSkuKey(metric.storeId(), metric.skuId()), metric);
        }
        Map<Mvp2StoreSkuKey, List<Mvp2CandidateResult>> candidatesByReceiver = new LinkedHashMap<>();
        for (Mvp2CandidateResult candidate : candidates) {
            candidatesByReceiver.computeIfAbsent(
                    new Mvp2StoreSkuKey(candidate.receiverStoreId(), candidate.skuId()), k -> new ArrayList<>()).add(candidate);
        }

        return new Mvp2CalculationResult(
                analysisDate, inputSnapshotVersion, metrics, candidates, metricsByStoreSku, candidatesByReceiver);
    }

    private static Mvp2StoreSkuKey storeSkuKey(Mvp2Anchor anchor) {
        return new Mvp2StoreSkuKey(anchor.storeId(), anchor.skuId());
    }

    /**
     * current-task.md section 1: the same {@code max(BASE target - projectedReceiverBeforeDemand, 0)}
     * formula the entity constructor used to compute itself, now produced once here from the raw
     * (non-effective) BASE rate so persistence only ever stores it, never recomputes it.
     */
    private static Long expectedShortageQuantity(
            InventoryProjection projection, BigDecimal baseDemandRate, int earliestArrivalLeadTimeDays, Mvp2Policy policy) {
        if (baseDemandRate == null || projection.isInputInvalid()) {
            return null;
        }
        long targetQuantity = projection.receiverTargetQuantity(
                baseDemandRate, earliestArrivalLeadTimeDays, policy.targetCoverageDays(), policy.displayMinimum());
        return Math.max(targetQuantity - projection.projectedReceiverBeforeDemand(), 0);
    }

    /** business-rules.md section 6: the same "cannot auto-quantify" gate {@link TransferScenarioSet} enforces. */
    private static boolean cannotAutoQuantify(DemandConfidence confidence, DemandSignalType signalType) {
        return (confidence == DemandConfidence.NONE || confidence == DemandConfidence.LOW)
                && signalType != DemandSignalType.VARIABLE;
    }

    /**
     * current-task.md step 4 evaluates lanes only for a "자동 계산 가능한 BASE 부족 receiver" --
     * auto-quantifiable AND actually short of its own BASE target. Without this second gate, a
     * well-stocked anchor whose signal simply happens to also be auto-quantifiable (a donor with
     * steady sales, for instance) would incorrectly be evaluated as a receiver against every
     * other same-SKU anchor too.
     * <p>
     * Builds a receiver-shaped projection that includes already-committed {@code APPROVED}/
     * {@code IN_TRANSIT} open transfers (both directions) but deliberately excludes confirmed
     * inbound: a receiver whose confirmed inbound alone would already close the gap (GS-05) must
     * still reach per-lane evaluation so {@code INBOUND_ALREADY_COVERS} is discovered and
     * recorded there, per section 7 condition 5 -- pre-filtering on inbound here would silently
     * drop that candidate instead of rejecting it with a reason. Open transfers, in contrast,
     * are already-committed movement, not a per-lane hypothesis, so they belong in this coarse
     * gate: a receiver already topped up by an approved inbound transfer must not be evaluated
     * as short, and one newly drained by an approved outbound transfer must not be skipped as
     * if it were still comfortable on {@code currentAvailable} alone.
     * <p>
     * A projection this gate finds {@link InventoryProjection#isInputInvalid()} (e.g. committed
     * outbound alone exceeding on-hand) is excluded from candidate evaluation entirely rather
     * than treated as an extreme shortage -- that is a data/state inconsistency, not a
     * legitimate business shortage signal. The authoritative, route-specific figure is still
     * decided per lane by {@link com.bapegg.stockpilot.demand.ApprovalBasisRecalculation}.
     */
    private static boolean hasMetricLevelBaseShortage(Mvp2Anchor anchor, AnchorEvidence evidence) {
        BigDecimal baseRate = evidence.rates.baseDemandRate();
        if (baseRate == null) {
            return false;
        }
        InventoryProjection shortageCheckProjection = InventoryProjection.calculate(
                anchor.currentOnHandQuantity(), anchor.currentReservedQuantity(),
                0, evidence.openTransferInbound, evidence.openTransferOutbound, 0, 0);
        if (shortageCheckProjection.isInputInvalid()) {
            return false;
        }
        long targetQuantity = shortageCheckProjection.receiverTargetQuantity(
                baseRate, evidence.earliestArrivalLeadTimeDays,
                anchor.policy().targetCoverageDays(), anchor.policy().displayMinimum());
        return targetQuantity > shortageCheckProjection.projectedReceiverBeforeDemand();
    }

    // ---- Steps 1-3: per-anchor statistics, signal, rates, quality flags, canonical projection ----

    private record AnchorEvidence(
            DemandObservationStatistics stats,
            DemandSignalClassification signal,
            DemandRateCalculation rates,
            InventoryProjection projection,
            int earliestArrivalLeadTimeDays,
            InventoryExceptionClassification provisionalException,
            Set<MetricQualityFlag> qualityFlags,
            int openTransferInbound,
            int openTransferOutbound,
            List<Mvp2InboundRow> confirmedCompleteInboundRows,
            long alreadyApprovedDraftQuantity) {
    }

    private static AnchorEvidence buildAnchorEvidence(Mvp2Anchor anchor, Mvp2InputGraph graph, LocalDate analysisDate) {
        DemandObservationStatistics stats = DemandObservationStatistics.calculate(anchor.observationWindow());

        LocalDate observationStart = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate observationEnd = analysisDate.minusDays(1);

        List<Integer> activeRouteLeadTimes = graph.routes().stream()
                .filter(r -> r.receiverStoreId().equals(anchor.storeId()) && r.route().active())
                .map(r -> r.route().leadTimeDays())
                .toList();
        PlanHorizon planHorizon = PlanHorizon.of(analysisDate, anchor.policy().targetCoverageDays(), activeRouteLeadTimes);

        List<DemandEvent> storeSkuEvents = graph.eventsByStoreSku()
                .getOrDefault(new Mvp2StoreSkuKey(anchor.storeId(), anchor.skuId()), List.of());
        List<DemandEvent> relevantEvents = RepresentativeEventSelection.selectRelevant(
                anchor.storeId(), anchor.skuId(), observationStart, observationEnd,
                analysisDate, planHorizon.endDate(), storeSkuEvents);
        DemandSignalClassification signal = DemandSignalClassification.classify(
                anchor.storeId(), anchor.skuId(), stats, planHorizon, storeSkuEvents);
        DemandRateCalculation rates = DemandRateCalculation.calculate(
                anchor.observationWindow(), stats, signal.signalType(), relevantEvents);

        Set<MetricQualityFlag> qualityFlags = new LinkedHashSet<>();
        if (stats.oosCensored()) {
            qualityFlags.add(MetricQualityFlag.OOS_CENSORED);
        }
        if (signal.incompleteEventData()) {
            qualityFlags.add(MetricQualityFlag.INCOMPLETE_EVENT_DATA);
        }
        OffsetDateTime analysisReferenceAt = analysisDate.plusDays(1).atStartOfDay(ASSUMPTION_TIMEZONE).toOffsetDateTime();
        boolean staleCurrentSnapshot = anchor.currentSnapshotAt() != null
                && java.time.Duration.between(anchor.currentSnapshotAt(), analysisReferenceAt).toHours() > 24;
        if (staleCurrentSnapshot || stats.invalidSnapshotDayCount() > 0) {
            qualityFlags.add(MetricQualityFlag.STALE_INVENTORY);
        }

        List<Mvp2InboundRow> storeSkuInbound = graph.inboundByStoreSku()
                .getOrDefault(new Mvp2StoreSkuKey(anchor.storeId(), anchor.skuId()), List.of());
        if (storeSkuInbound.stream().anyMatch(r -> !r.isComplete())) {
            qualityFlags.add(MetricQualityFlag.MISSING_INBOUND);
        }
        List<Mvp2InboundRow> confirmedCompleteInbound = storeSkuInbound.stream()
                .filter(r -> r.inboundStatus() == InboundStatus.CONFIRMED && r.isComplete())
                .toList();

        // business-rules.md section 4: "그 외 하나 이상의 quality flag는 LOW여야 하며" -- STALE_INVENTORY
        // and MISSING_INBOUND are computed here in the orchestrator (DemandSignalClassification has
        // no access to current-snapshot timing or inbound rows), so unlike OOS_CENSORED/
        // INCOMPLETE_EVENT_DATA they cannot already be folded into classify()'s own confidence.
        // DATA_INSUFFICIENT's NONE confidence is left untouched -- section 4's confidence table
        // reserves NONE for that case alone, and it already blocks auto-quantification regardless.
        if (signal.confidence() != DemandConfidence.NONE
                && (qualityFlags.contains(MetricQualityFlag.STALE_INVENTORY)
                        || qualityFlags.contains(MetricQualityFlag.MISSING_INBOUND))) {
            signal = new DemandSignalClassification(
                    signal.signalType(), DemandConfidence.LOW, signal.relevantEvent(), signal.incompleteEventData());
        }

        int openTransferInbound = sumOpenTransferQuantity(graph.openTransfers(), anchor.storeId(), anchor.skuId(), true);
        int openTransferOutbound = sumOpenTransferQuantity(graph.openTransfers(), anchor.storeId(), anchor.skuId(), false);
        long alreadyApprovedDraftQuantity = graph.activeApprovedDraftQuantityByDonorSku()
                .getOrDefault(new Mvp2DonorSkuKey(anchor.storeId(), anchor.skuId()), 0L);

        List<Integer> candidateLeadTimes = new ArrayList<>(activeRouteLeadTimes);
        for (Mvp2InboundRow row : confirmedCompleteInbound) {
            LocalDate etaLocalDate = row.etaAt().atZoneSameInstant(ASSUMPTION_TIMEZONE).toLocalDate();
            candidateLeadTimes.add((int) Math.max(0, ChronoUnit.DAYS.between(analysisDate, etaLocalDate)));
        }
        int earliestArrivalLeadTimeDays = candidateLeadTimes.stream().mapToInt(Integer::intValue).min()
                .orElse(DemandAnalysisRules.PLAN_HORIZON_NO_ROUTE_FALLBACK_DAYS);

        int inboundBeforeTransfer = sumConfirmedInboundBeforeCutoff(
                confirmedCompleteInbound, analysisDate.plusDays(earliestArrivalLeadTimeDays));
        int inboundBeforeDispatch = sumConfirmedInboundBeforeCutoff(confirmedCompleteInbound, analysisDate);

        InventoryProjection projection = InventoryProjection.calculate(
                anchor.currentOnHandQuantity(), anchor.currentReservedQuantity(),
                inboundBeforeTransfer, openTransferInbound, openTransferOutbound,
                inboundBeforeDispatch, Math.toIntExact(alreadyApprovedDraftQuantity));

        InventoryExceptionClassification provisionalException = InventoryExceptionClassification.classify(
                projection, signal.confidence(), rates, earliestArrivalLeadTimeDays,
                anchor.policy().targetCoverageDays(), anchor.policy().retainedDays(),
                anchor.policy().displayMinimum(), anchor.policy().safetyStock(), false);

        return new AnchorEvidence(stats, signal, rates, projection, earliestArrivalLeadTimeDays,
                provisionalException, qualityFlags, openTransferInbound, openTransferOutbound,
                confirmedCompleteInbound, alreadyApprovedDraftQuantity);
    }

    private static int sumOpenTransferQuantity(
            List<Mvp2OpenTransferRow> rows, String storeId, String skuId, boolean inboundToStore) {
        long sum = 0;
        for (Mvp2OpenTransferRow row : rows) {
            if (!row.skuId().equals(skuId)) {
                continue;
            }
            if (row.transferStatus() != OpenTransferStatus.APPROVED && row.transferStatus() != OpenTransferStatus.IN_TRANSIT) {
                continue;
            }
            if (inboundToStore && row.receiverStoreId().equals(storeId)) {
                sum += row.quantity();
            } else if (!inboundToStore && row.donorStoreId().equals(storeId)) {
                sum += row.quantity();
            }
        }
        return safeIntSum(sum, "store=" + storeId + ", sku=" + skuId, "openTransferQuantity");
    }

    private static int sumConfirmedInboundBeforeCutoff(List<Mvp2InboundRow> confirmedCompleteRows, LocalDate cutoffDate) {
        long sum = 0;
        String context = confirmedCompleteRows.isEmpty() ? "" : confirmedCompleteRows.get(0).storeId();
        for (Mvp2InboundRow row : confirmedCompleteRows) {
            LocalDate etaLocalDate = row.etaAt().atZoneSameInstant(ASSUMPTION_TIMEZONE).toLocalDate();
            if (!etaLocalDate.isAfter(cutoffDate)) {
                sum += row.quantity();
            }
        }
        return safeIntSum(sum, "store=" + context, "confirmedInboundQuantity");
    }

    /**
     * Every individual quantity summed here already passed {@code Mvp2InputAdapter}'s own
     * 32-bit range check, but a plain {@code int +=} accumulation of several such
     * individually-valid rows can still silently wrap past {@code Integer.MAX_VALUE}. Summing as
     * {@code long} and range-checking the total, rather than each addend, is what actually
     * prevents that -- this must stay a range check on the aggregate, not a per-row one.
     */
    private static int safeIntSum(long sum, String context, String fieldName) {
        if (sum < Integer.MIN_VALUE || sum > Integer.MAX_VALUE) {
            throw new InputContractViolationException(
                    context + ": " + fieldName + "=" + sum + " does not fit the domain's 32-bit integer range.");
        }
        return (int) sum;
    }

    // ---- Step 4-5: candidate evaluation --------------------------------------------------------

    private static Mvp2CandidateResult evaluateCandidate(
            Mvp2InputGraph graph, LocalDate analysisDate,
            Mvp2Anchor receiverAnchor, AnchorEvidence receiverEvidence,
            Mvp2Anchor donorAnchor, AnchorEvidence donorEvidence) {
        String skuId = receiverAnchor.skuId();
        List<Mvp2Route> routeRows = graph.routesByStorePair()
                .getOrDefault(new Mvp2StorePairKey(donorAnchor.storeId(), receiverAnchor.storeId()), List.of());
        Long routeId = routeRows.isEmpty() ? null : routeRows.get(0).routeId();
        // A synthetic inactive route when no row exists at all -- lets the same pure evaluation
        // path produce ROUTE_NOT_ALLOWED (and any co-occurring OWNER_MISMATCH) uniformly, per
        // section 7 condition 3, instead of a separate hand-built rejection.
        TransferRoute route = routeRows.isEmpty() ? new TransferRoute(false, false, 0, 1, 1, 1) : routeRows.get(0).route();

        LocalDate receiverInboundCutoff = analysisDate.plusDays(route.leadTimeDays());
        int receiverInboundBeforeTransfer =
                sumConfirmedInboundBeforeCutoff(receiverEvidence.confirmedCompleteInboundRows, receiverInboundCutoff);
        InventoryProjection receiverProjection = InventoryProjection.calculate(
                receiverAnchor.currentOnHandQuantity(), receiverAnchor.currentReservedQuantity(),
                receiverInboundBeforeTransfer, receiverEvidence.openTransferInbound, receiverEvidence.openTransferOutbound,
                0, 0);

        int donorInboundBeforeDispatch = sumConfirmedInboundBeforeCutoff(donorEvidence.confirmedCompleteInboundRows, analysisDate);
        InventoryProjection donorProjection = InventoryProjection.calculate(
                donorAnchor.currentOnHandQuantity(), donorAnchor.currentReservedQuantity(),
                0, 0, donorEvidence.openTransferOutbound,
                donorInboundBeforeDispatch, Math.toIntExact(donorEvidence.alreadyApprovedDraftQuantity));

        boolean pendingTransferConflict = graph.openTransfersByLane()
                .getOrDefault(new Mvp2LaneKey(donorAnchor.storeId(), receiverAnchor.storeId(), skuId), List.of())
                .stream().anyMatch(r -> r.transferStatus() == OpenTransferStatus.REQUESTED);

        BigDecimal effectiveReceiverBaseRate = EffectiveReceiverBaseRate.calculate(
                receiverEvidence.rates.baseDemandRate(), receiverEvidence.signal.signalType(),
                receiverEvidence.signal.relevantEvent(), analysisDate, route, receiverAnchor.policy().targetCoverageDays());

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                skuId, receiverAnchor.storeId(), receiverAnchor.storeOwnerCode(),
                donorAnchor.storeId(), donorAnchor.storeOwnerCode(),
                route, receiverProjection, effectiveReceiverBaseRate,
                receiverAnchor.policy().targetCoverageDays(), receiverAnchor.policy().displayMinimum(),
                receiverAnchor.policy().maximumCapacity(),
                donorProjection, donorEvidence.rates.highDemandRate(), donorAnchor.policy().retainedDays(),
                donorAnchor.policy().displayMinimum(), donorAnchor.policy().safetyStock(),
                receiverInboundBeforeTransfer > 0, pendingTransferConflict);

        boolean eligible = recalculation.eligible();
        List<TransferCandidateRejectionReason> reasons = List.copyOf(recalculation.candidateEvaluation().reasons());
        DemandSignalType signalType = receiverEvidence.signal.signalType();

        CandidateStatus candidateStatus = eligible ? CandidateStatus.ELIGIBLE : CandidateStatus.REJECTED;
        RecommendationMode mode;
        List<TransferScenarioResult> scenarios = List.of();
        Integer recommendedQuantity = null;
        if (eligible) {
            TransferScenarioSet scenarioSet = TransferScenarioSet.calculate(
                    signalType, receiverEvidence.signal.confidence(), receiverEvidence.rates,
                    analysisDate, receiverEvidence.signal.relevantEvent(),
                    receiverProjection, receiverAnchor.policy().targetCoverageDays(), receiverAnchor.policy().displayMinimum(),
                    receiverAnchor.policy().maximumCapacity(),
                    donorProjection, donorEvidence.rates, donorAnchor.policy().retainedDays(),
                    donorAnchor.policy().displayMinimum(), donorAnchor.policy().safetyStock(),
                    route);
            scenarios = scenarioSet.scenarios();
            if (signalType == DemandSignalType.VARIABLE) {
                mode = RecommendationMode.COMPARISON_ONLY;
            } else {
                mode = RecommendationMode.RECOMMENDED;
                long baseQuantity = scenarios.stream()
                        .filter(s -> s.scenarioType() == TransferScenarioType.BASE)
                        .findFirst().orElseThrow().scenarioQuantity();
                recommendedQuantity = Math.toIntExact(baseQuantity);
            }
        } else {
            mode = RecommendationMode.NONE;
        }

        long receiverTargetQuantity = receiverProjection.receiverTargetQuantity(
                effectiveReceiverBaseRate, route.leadTimeDays(),
                receiverAnchor.policy().targetCoverageDays(), receiverAnchor.policy().displayMinimum());
        int receiverShortageQuantity = Math.toIntExact(
                Math.max(receiverTargetQuantity - receiverProjection.projectedReceiverBeforeDemand(), 0));
        // The receiver's projected position AT arrival -- current position plus already-committed
        // evidence, minus the effective-BASE demand expected during the route's own lead time --
        // not merely the pre-arrival snapshot recalculation.receiverProjectedBeforeDemand() is.
        long projectedReceiverAtArrival =
                receiverProjection.receiverAtArrivalWithoutNewTransfer(effectiveReceiverBaseRate, route.leadTimeDays());

        return new Mvp2CandidateResult(
                receiverAnchor.storeId(), donorAnchor.storeId(), skuId, routeId,
                candidateStatus, 1, mode,
                receiverShortageQuantity, Math.toIntExact(recalculation.donorTransferableQuantity()), recommendedQuantity,
                projectedReceiverAtArrival, recalculation.donorProjectedAtDispatch(),
                recalculation.receiverCapacityRemaining(),
                reasons, recalculation.packageMultiple(), scenarios);
    }
}
