package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.DemandEvent;
import com.bapegg.stockpilot.demand.EffectiveReceiverBaseRate;
import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.PlanHorizon;
import com.bapegg.stockpilot.demand.RepresentativeEventSelection;
import com.bapegg.stockpilot.demand.TransferRoute;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.DraftStatus;
import com.bapegg.stockpilot.rebalance.InboundStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import com.bapegg.stockpilot.rebalance.SpDemandEvent;
import com.bapegg.stockpilot.rebalance.SpDemandEventRepository;
import com.bapegg.stockpilot.rebalance.SpInboundSchedule;
import com.bapegg.stockpilot.rebalance.SpInboundScheduleRepository;
import com.bapegg.stockpilot.rebalance.SpOpenTransfer;
import com.bapegg.stockpilot.rebalance.SpOpenTransferRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicy;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and cross-validates the current approval basis for one locked recommendation, per
 * {@code knowledge/business-rules.md} section 10. Extracted so
 * {@link ApprovalTransactionExecutor} (the {@code APPROVED} path) and
 * {@code ManualQuantityTestExecutor} (the side-effect-free quantity-test preview) share the exact
 * same version check, queries and identity cross-validation instead of either one copying them.
 * <p>
 * Not itself {@code @Transactional}: both callers invoke {@link #validateCurrent} and
 * {@link #load} from inside their own already-active write transaction, right after taking the
 * recommendation lock (and, for {@link #load}, immediately before it takes the donor snapshot
 * lock) -- the same recommendation-then-donor lock order every approval-basis read uses.
 * {@link #load} assumes {@link #validateCurrent} already passed for the same call; it does not
 * repeat that check.
 */
@Service
public class CurrentApprovalBasisLoader {

    private static final ZoneId ASSUMPTION_TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final Set<OpenTransferStatus> COMMITTED_OPEN_TRANSFER_STATUSES =
            EnumSet.of(OpenTransferStatus.APPROVED, OpenTransferStatus.IN_TRANSIT);
    // Per redesign spec section 4.6: APPROVED/IN_TRANSIT are already reflected once in
    // COMMITTED_OPEN_TRANSFER_STATUSES's projection quantities above -- re-blocking the same lane
    // as a "pending conflict" double-penalizes quantity already accounted for. Only a REQUESTED
    // (not-yet-approved) request on the same donor-receiver-SKU lane is a genuine conflict.
    private static final Set<OpenTransferStatus> PENDING_CONFLICT_STATUSES =
            EnumSet.of(OpenTransferStatus.REQUESTED);
    private static final Set<DraftStatus> ACTIVE_DRAFT_STATUSES =
            EnumSet.of(DraftStatus.CREATED, DraftStatus.READY, DraftStatus.SENT, DraftStatus.ACCEPTED);

    private final SpAnalysisRunRepository analysisRunRepository;
    private final SpInventorySnapshotRepository snapshotRepository;
    private final SpStoreTransferRouteRepository routeRepository;
    private final SpStoreSkuPolicyRepository policyRepository;
    private final SpOpenTransferRepository openTransferRepository;
    private final SpInboundScheduleRepository inboundScheduleRepository;
    private final SpTransferDraftRepository transferDraftRepository;
    private final SpStoreRepository storeRepository;
    private final SpDemandEventRepository demandEventRepository;

    public CurrentApprovalBasisLoader(
            SpAnalysisRunRepository analysisRunRepository,
            SpInventorySnapshotRepository snapshotRepository,
            SpStoreTransferRouteRepository routeRepository,
            SpStoreSkuPolicyRepository policyRepository,
            SpOpenTransferRepository openTransferRepository,
            SpInboundScheduleRepository inboundScheduleRepository,
            SpTransferDraftRepository transferDraftRepository,
            SpStoreRepository storeRepository,
            SpDemandEventRepository demandEventRepository) {
        this.analysisRunRepository = analysisRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.routeRepository = routeRepository;
        this.policyRepository = policyRepository;
        this.openTransferRepository = openTransferRepository;
        this.inboundScheduleRepository = inboundScheduleRepository;
        this.transferDraftRepository = transferDraftRepository;
        this.storeRepository = storeRepository;
        this.demandEventRepository = demandEventRepository;
    }

    /** Version/currency check only -- no lock, no query beyond the current-run lookup. */
    public void validateCurrent(
            SpRebalanceRecommendation recommendation, SpAnalysisRun analysisRun,
            Long analysisRunId, String inputSnapshotVersion, String ruleVersion, int candidateVersion) {
        boolean versionsMatch = analysisRun.getAnalysisRunId().equals(analysisRunId)
                && analysisRun.getInputSnapshotVersion().equals(inputSnapshotVersion)
                && analysisRun.getRuleVersion().equals(ruleVersion)
                && recommendation.getCandidateVersion() == candidateVersion;
        if (!versionsMatch || analysisRun.getRunStatus() != AnalysisRunStatus.COMPLETED) {
            throw staleRecommendation();
        }
        SpAnalysisRun currentRun = analysisRunRepository
                .findFirstByRuleVersionAndRunStatusOrderByAnalysisDateDescCompletedAtDescAnalysisRunIdDesc(
                        analysisRun.getRuleVersion(), AnalysisRunStatus.COMPLETED)
                .orElseThrow(CurrentApprovalBasisLoader::staleRecommendation);
        if (!currentRun.getAnalysisRunId().equals(analysisRun.getAnalysisRunId())) {
            throw staleRecommendation();
        }
    }

    /**
     * Locks the donor snapshot (recommendation must already be locked by the caller), then loads
     * and cross-validates route/policy/inbound/open-transfer/draft evidence. Assumes
     * {@link #validateCurrent} already passed for this exact call.
     */
    public LoadedApprovalBasis load(
            SpRebalanceRecommendation recommendation, SpAnalysisRun analysisRun, String inputSnapshotVersion) {
        SpInventoryMetric receiverMetric = recommendation.getReceiverMetric();
        SpInventoryMetric donorMetric = recommendation.getDonorMetric();
        if (!donorMetric.getAnalysisRun().getAnalysisRunId().equals(analysisRun.getAnalysisRunId())) {
            throw staleRecommendation();
        }

        SpInventorySnapshot receiverSnapshot = receiverMetric.getInventorySnapshot();
        SpInventorySnapshot lockedDonorSnapshot = snapshotRepository
                .lockById(donorMetric.getInventorySnapshot().getInventorySnapshotId())
                .orElseThrow(() -> new ApprovalTransactionException(ApprovalErrorCode.INTERNAL_SERVER_ERROR,
                        "Donor snapshot referenced by recommendation " + recommendation.getRecommendationId()
                                + " no longer exists."));

        String receiverStoreId = receiverSnapshot.getStoreId();
        String donorStoreId = lockedDonorSnapshot.getStoreId();
        String skuId = receiverSnapshot.getSkuId();

        if (!skuId.equals(lockedDonorSnapshot.getSkuId()) || receiverStoreId.equals(donorStoreId)
                || !inputSnapshotVersion.equals(receiverSnapshot.getInputSnapshotVersion())
                || !inputSnapshotVersion.equals(lockedDonorSnapshot.getInputSnapshotVersion())) {
            throw staleRecommendation();
        }

        Long routeId = recommendation.getRouteId();
        if (routeId == null) {
            throw staleRecommendation();
        }
        SpStoreTransferRoute routeEntity = routeRepository.findById(routeId)
                .orElseThrow(CurrentApprovalBasisLoader::staleRecommendation);
        if (!routeEntity.isActive()
                || !routeEntity.getDonorStoreId().equals(donorStoreId)
                || !routeEntity.getReceiverStoreId().equals(receiverStoreId)
                || !routeEntity.getInputSnapshotVersion().equals(inputSnapshotVersion)) {
            throw staleRecommendation();
        }
        TransferRoute route = routeEntity.toTransferRoute();

        // Per business-rules.md section 10: a missing store-SKU policy row is not stale -- Batch
        // and approval/MANUAL both fall back to section 1's approved ASSUMPTION defaults. A
        // missing *route* row is a different case entirely (staleRecommendation() above) and is
        // never defaulted this way.
        EffectivePolicy receiverPolicy = resolvePolicy(receiverStoreId, skuId, inputSnapshotVersion);
        EffectivePolicy donorPolicy = resolvePolicy(donorStoreId, skuId, inputSnapshotVersion);

        // donorMetric.getBaseDemandRate() is intentionally NOT checked here: V6 allows it to be
        // nullable, accepted approval calculation never uses it (only donor HIGH), and it exists
        // only for the manual quantity-test preview's own donor coverage-days display -- see
        // TransferEffectProjection, which already tolerates a null rate by returning a null
        // coverage figure rather than requiring one.
        if (receiverMetric.getBaseDemandRate() == null || donorMetric.getHighDemandRate() == null) {
            throw staleRecommendation();
        }

        LocalDate analysisDate = analysisRun.getAnalysisDate();
        BigDecimal effectiveReceiverBaseRate;
        try {
            effectiveReceiverBaseRate = effectiveReceiverBaseRate(
                    receiverMetric, receiverStoreId, skuId, inputSnapshotVersion, analysisDate, route, receiverPolicy);
        } catch (IllegalArgumentException e) {
            throw staleRecommendation();
        }
        LocalDate receiverInboundCutoff = analysisDate.plusDays(route.leadTimeDays());
        LocalDate donorInboundCutoff = analysisDate;

        int receiverInboundArriving =
                sumConfirmedInbound(receiverStoreId, skuId, inputSnapshotVersion, receiverInboundCutoff);
        int donorInboundArriving = sumConfirmedInbound(donorStoreId, skuId, inputSnapshotVersion, donorInboundCutoff);

        int receiverOpenInbound = sumOpenTransferQuantity(
                openTransferRepository.findByReceiverStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
                        receiverStoreId, skuId, inputSnapshotVersion, COMMITTED_OPEN_TRANSFER_STATUSES));
        int receiverOpenOutbound = sumOpenTransferQuantity(
                openTransferRepository.findByDonorStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
                        receiverStoreId, skuId, inputSnapshotVersion, COMMITTED_OPEN_TRANSFER_STATUSES));
        int donorOpenOutbound = sumOpenTransferQuantity(
                openTransferRepository.findByDonorStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
                        donorStoreId, skuId, inputSnapshotVersion, COMMITTED_OPEN_TRANSFER_STATUSES));

        boolean pendingTransferConflict = openTransferRepository
                .existsByDonorStoreIdAndReceiverStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
                        donorStoreId, receiverStoreId, skuId, inputSnapshotVersion, PENDING_CONFLICT_STATUSES);

        int donorAlreadyApprovedDraftQuantity = transferDraftRepository.sumActiveQuantityByDonorStoreIdAndSkuId(
                donorStoreId, skuId, ACTIVE_DRAFT_STATUSES, DecisionStatus.APPROVED);

        InventoryProjection receiverProjection;
        InventoryProjection donorProjection;
        try {
            receiverProjection = InventoryProjection.calculate(
                    receiverSnapshot.getOnHandQuantity(), receiverSnapshot.getReservedQuantity(),
                    receiverInboundArriving, receiverOpenInbound, receiverOpenOutbound, 0, 0);
            donorProjection = InventoryProjection.calculate(
                    lockedDonorSnapshot.getOnHandQuantity(), lockedDonorSnapshot.getReservedQuantity(),
                    0, 0, donorOpenOutbound, donorInboundArriving, donorAlreadyApprovedDraftQuantity);
        } catch (IllegalArgumentException e) {
            throw staleRecommendation();
        }
        if (receiverProjection.isInputInvalid() || donorProjection.isInputInvalid()) {
            throw staleRecommendation();
        }

        SpStore receiverStore = storeRepository.findById(receiverStoreId)
                .orElseThrow(CurrentApprovalBasisLoader::staleRecommendation);
        SpStore donorStore = storeRepository.findById(donorStoreId)
                .orElseThrow(CurrentApprovalBasisLoader::staleRecommendation);

        return new LoadedApprovalBasis(
                skuId, receiverStoreId, receiverStore.getInventoryOwnerCode(),
                donorStoreId, donorStore.getInventoryOwnerCode(),
                route, analysisDate,
                receiverProjection, effectiveReceiverBaseRate,
                receiverPolicy.targetCoverageDays(), receiverPolicy.displayMinimum(),
                receiverPolicy.maximumCapacity(),
                donorProjection, donorMetric.getBaseDemandRate(), donorMetric.getHighDemandRate(),
                donorPolicy.retainedDays(), donorPolicy.displayMinimum(), donorPolicy.safetyStock(),
                receiverInboundArriving > 0, pendingTransferConflict);
    }

    /** Missing-row-defaults-to-{@link DemandAnalysisRules} policy, per business-rules.md section 10. */
    private EffectivePolicy resolvePolicy(String storeId, String skuId, String inputSnapshotVersion) {
        return policyRepository.findByStoreIdAndSkuIdAndInputSnapshotVersion(storeId, skuId, inputSnapshotVersion)
                .map(p -> new EffectivePolicy(
                        p.getDisplayMinimum(), p.getSafetyStock(), p.getMaximumCapacity(),
                        p.getTargetCoverageDays(), p.getRetainedDays()))
                .orElseGet(() -> new EffectivePolicy(
                        DemandAnalysisRules.DEFAULT_DISPLAY_MINIMUM, DemandAnalysisRules.DEFAULT_SAFETY_STOCK,
                        DemandAnalysisRules.DEFAULT_MAXIMUM_CAPACITY, DemandAnalysisRules.DEFAULT_TARGET_COVERAGE_DAYS,
                        DemandAnalysisRules.DEFAULT_RETAINED_DAYS));
    }

    /**
     * The current, route-uplifted receiver BASE rate, per business-rules.md section 10's shared
     * current-basis contract: relevant events are those overlapping either the 28-day observation
     * window or the receiver's full active-route plan horizon, sorted {@code (startDate,
     * eventCode)}; the first is representative; its uplift applies to {@code baselineBaseRate}
     * only when the stored signal is {@code KNOWN_EVENT} and the representative event overlaps
     * this specific candidate route's arrival-through-target-coverage window. Used for the
     * recommendation, comparison and final approval revalidation alike -- never re-derived
     * per caller.
     */
    private BigDecimal effectiveReceiverBaseRate(
            SpInventoryMetric receiverMetric, String receiverStoreId, String skuId, String inputSnapshotVersion,
            LocalDate analysisDate, TransferRoute route, EffectivePolicy receiverPolicy) {
        List<SpStoreTransferRoute> receiverRoutes = routeRepository
                .findByReceiverStoreIdAndInputSnapshotVersion(receiverStoreId, inputSnapshotVersion);
        List<Integer> activeLeadTimes = receiverRoutes.stream()
                .filter(SpStoreTransferRoute::isActive)
                .map(SpStoreTransferRoute::getLeadTimeDays)
                .toList();
        PlanHorizon planHorizon = PlanHorizon.of(analysisDate, receiverPolicy.targetCoverageDays(), activeLeadTimes);

        LocalDate observationStart = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate observationEnd = analysisDate.minusDays(1);

        List<DemandEvent> events = demandEventRepository
                .findByStoreIdAndSkuIdAndInputSnapshotVersion(receiverStoreId, skuId, inputSnapshotVersion)
                .stream().map(SpDemandEvent::toDemandEvent).toList();
        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                receiverStoreId, skuId, observationStart, observationEnd,
                planHorizon.analysisDate(), planHorizon.endDate(), events);
        Optional<DemandEvent> representativeEvent = RepresentativeEventSelection.representative(relevant);

        return EffectiveReceiverBaseRate.calculate(
                receiverMetric.getBaseDemandRate(), receiverMetric.getPrimaryDemandSignalType(),
                representativeEvent.orElse(null), analysisDate, route, receiverPolicy.targetCoverageDays());
    }

    private record EffectivePolicy(
            int displayMinimum, int safetyStock, int maximumCapacity, int targetCoverageDays, int retainedDays) {
    }

    private int sumConfirmedInbound(String storeId, String skuId, String inputSnapshotVersion, LocalDate cutoffDate) {
        List<SpInboundSchedule> rows = inboundScheduleRepository
                .findByStoreIdAndSkuIdAndInputSnapshotVersionAndInboundStatus(
                        storeId, skuId, inputSnapshotVersion, InboundStatus.CONFIRMED);
        int sum = 0;
        for (SpInboundSchedule row : rows) {
            if (row.getEtaAt() == null || row.getQuantity() == null) {
                throw staleRecommendation();
            }
            LocalDate etaDate = row.getEtaAt().atZoneSameInstant(ASSUMPTION_TIMEZONE).toLocalDate();
            if (!etaDate.isAfter(cutoffDate)) {
                sum += row.getQuantity();
            }
        }
        return sum;
    }

    private static int sumOpenTransferQuantity(List<SpOpenTransfer> rows) {
        int sum = 0;
        for (SpOpenTransfer row : rows) {
            sum += row.getQuantity();
        }
        return sum;
    }

    private static ApprovalTransactionException staleRecommendation() {
        return new ApprovalTransactionException(
                ApprovalErrorCode.STALE_RECOMMENDATION, "Recommendation is no longer current.");
    }
}
