package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.api.error.ApiFieldError;
import com.bapegg.stockpilot.catalog.SpProduct;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventorySeverity;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.inventory.SpDailySale;
import com.bapegg.stockpilot.inventory.SpDailySaleRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import com.bapegg.stockpilot.rebalance.SpCandidateReason;
import com.bapegg.stockpilot.rebalance.SpCandidateReasonRepository;
import com.bapegg.stockpilot.rebalance.SpDemandEvent;
import com.bapegg.stockpilot.rebalance.SpDemandEventRepository;
import com.bapegg.stockpilot.rebalance.SpInboundSchedule;
import com.bapegg.stockpilot.rebalance.SpInboundScheduleRepository;
import com.bapegg.stockpilot.rebalance.SpOpenTransfer;
import com.bapegg.stockpilot.rebalance.SpOpenTransferRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecision;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenario;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenarioRepository;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicy;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only queries over the MVP-2 run-bound inventory-exception queue and its 28-day
 * evidence/candidate/scenario detail, per current-task.md (Phase 4 read API). Delegates back to
 * {@link InventoryExceptionService} for any metric whose run is still {@code MVP-1}, per section
 * 1.3 -- this service owns only the {@code ruleVersion=MVP-2} shape.
 */
@Service
@Transactional(readOnly = true)
public class Mvp2InventoryExceptionQueryService {

    private static final Logger log = LoggerFactory.getLogger(Mvp2InventoryExceptionQueryService.class);

    private static final String ASSUMPTION_TYPE = "ASSUMPTION";
    private static final String ASSUMPTION_NOTICE = "MVP-2 데모 규칙이며 실제 기업 정책이 아닙니다.";
    private static final ZoneId ASSUMPTION_TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<InventoryExceptionType> ALLOWED_EXCEPTION_TYPES = EnumSet.of(
            InventoryExceptionType.STOCKOUT_RISK, InventoryExceptionType.OVERSTOCK,
            InventoryExceptionType.REVIEW_REQUIRED, InventoryExceptionType.NON_ACTIONABLE);
    private static final Set<OpenTransferStatus> OPEN_TRANSFER_STATUSES = EnumSet.of(
            OpenTransferStatus.REQUESTED, OpenTransferStatus.APPROVED, OpenTransferStatus.IN_TRANSIT);
    private static final Comparator<Mvp2InventoryExceptionDetail.CandidateDetail> CANDIDATE_ORDER = Comparator
            .comparing((Mvp2InventoryExceptionDetail.CandidateDetail c) -> c.candidateStatus() == CandidateStatus.ELIGIBLE ? 0 : 1)
            .thenComparing(c -> recommendationModeRank(c.recommendationMode()))
            .thenComparing(Mvp2InventoryExceptionDetail.CandidateDetail::counterpartStoreId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Mvp2InventoryExceptionDetail.CandidateDetail::recommendationId);

    private final SpAnalysisRunRepository analysisRunRepository;
    private final SpInventoryMetricRepository metricRepository;
    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpCandidateReasonRepository candidateReasonRepository;
    private final SpRebalanceScenarioRepository scenarioRepository;
    private final SpRebalanceDecisionRepository decisionRepository;
    private final SpStoreTransferRouteRepository storeTransferRouteRepository;
    private final SpStoreSkuPolicyRepository storeSkuPolicyRepository;
    private final SpDemandEventRepository demandEventRepository;
    private final SpInboundScheduleRepository inboundScheduleRepository;
    private final SpOpenTransferRepository openTransferRepository;
    private final SpDailySaleRepository dailySaleRepository;
    private final SpInventorySnapshotRepository inventorySnapshotRepository;
    private final SpStoreRepository storeRepository;
    private final InventoryExceptionService inventoryExceptionService;

    public Mvp2InventoryExceptionQueryService(
            SpAnalysisRunRepository analysisRunRepository,
            SpInventoryMetricRepository metricRepository,
            SpRebalanceRecommendationRepository recommendationRepository,
            SpCandidateReasonRepository candidateReasonRepository,
            SpRebalanceScenarioRepository scenarioRepository,
            SpRebalanceDecisionRepository decisionRepository,
            SpStoreTransferRouteRepository storeTransferRouteRepository,
            SpStoreSkuPolicyRepository storeSkuPolicyRepository,
            SpDemandEventRepository demandEventRepository,
            SpInboundScheduleRepository inboundScheduleRepository,
            SpOpenTransferRepository openTransferRepository,
            SpDailySaleRepository dailySaleRepository,
            SpInventorySnapshotRepository inventorySnapshotRepository,
            SpStoreRepository storeRepository,
            InventoryExceptionService inventoryExceptionService) {
        this.analysisRunRepository = analysisRunRepository;
        this.metricRepository = metricRepository;
        this.recommendationRepository = recommendationRepository;
        this.candidateReasonRepository = candidateReasonRepository;
        this.scenarioRepository = scenarioRepository;
        this.decisionRepository = decisionRepository;
        this.storeTransferRouteRepository = storeTransferRouteRepository;
        this.storeSkuPolicyRepository = storeSkuPolicyRepository;
        this.demandEventRepository = demandEventRepository;
        this.inboundScheduleRepository = inboundScheduleRepository;
        this.openTransferRepository = openTransferRepository;
        this.dailySaleRepository = dailySaleRepository;
        this.inventorySnapshotRepository = inventorySnapshotRepository;
        this.storeRepository = storeRepository;
        this.inventoryExceptionService = inventoryExceptionService;
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    public Mvp2InventoryExceptionPage listExceptions(
            LocalDate analysisDate,
            Long analysisRunId,
            List<String> exceptionTypeParams,
            List<String> severityParams,
            List<String> signalParams,
            List<String> confidenceParams,
            List<String> qualityFlagParams,
            String storeIdParam,
            String skuIdParam,
            Boolean hasExecutableCandidate,
            Integer pageParam,
            Integer sizeParam) {

        List<ApiFieldError> fieldErrors = new ArrayList<>();

        if (analysisRunId != null && analysisDate != null) {
            fieldErrors.add(new ApiFieldError("analysisDate", "FORBIDDEN", "analysisRunId와 analysisDate를 함께 지정할 수 없습니다."));
        }
        if (analysisRunId == null) {
            fieldErrors.add(new ApiFieldError("analysisRunId", "REQUIRED", "run-bound 조회에는 analysisRunId가 필요합니다."));
        } else if (analysisRunId <= 0) {
            fieldErrors.add(new ApiFieldError("analysisRunId", "FORMAT", "analysisRunId는 양의 정수여야 합니다."));
        }

        Set<InventoryExceptionType> exceptionTypes = parseEnumFilter(
                "exceptionType", exceptionTypeParams, InventoryExceptionType.class, ALLOWED_EXCEPTION_TYPES, fieldErrors);
        Set<InventorySeverity> severities = parseEnumFilter(
                "severity", severityParams, InventorySeverity.class, EnumSet.allOf(InventorySeverity.class), fieldErrors);
        Set<DemandSignalType> signals = parseEnumFilter(
                "signal", signalParams, DemandSignalType.class, EnumSet.allOf(DemandSignalType.class), fieldErrors);
        Set<DemandConfidence> confidences = parseEnumFilter(
                "confidence", confidenceParams, DemandConfidence.class, EnumSet.allOf(DemandConfidence.class), fieldErrors);
        Set<MetricQualityFlag> qualityFlags = parseEnumFilter(
                "qualityFlag", qualityFlagParams, MetricQualityFlag.class, EnumSet.allOf(MetricQualityFlag.class), fieldErrors);

        String storeId = validateExactMatch("storeId", storeIdParam, fieldErrors);
        String skuId = validateExactMatch("skuId", skuIdParam, fieldErrors);

        int page = pageParam == null ? 0 : pageParam;
        if (page < 0) {
            fieldErrors.add(new ApiFieldError("page", "FORMAT", "page는 0 이상이어야 합니다."));
        }
        int size = sizeParam == null ? DEFAULT_PAGE_SIZE : sizeParam;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            fieldErrors.add(new ApiFieldError("size", "FORMAT", "size는 1~100 사이여야 합니다."));
        }

        if (!fieldErrors.isEmpty()) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "Invalid inventory-exception list request.", fieldErrors);
        }

        SpAnalysisRun run = analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ANALYSIS_NOT_FOUND, "No analysis run found for id " + analysisRunId));
        if (!DemandAnalysisRules.RULE_VERSION.equals(run.getRuleVersion())) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "analysisRunId does not reference an MVP-2 run.",
                    List.of(new ApiFieldError("analysisRunId", "FORMAT", "지정한 analysisRunId는 MVP-2 rule version이 아닙니다.")));
        }
        if (run.getRunStatus() != AnalysisRunStatus.COMPLETED) {
            throw new ApiException(ApiErrorCode.ANALYSIS_RESULTS_NOT_READY, "Analysis run " + analysisRunId + " is not completed.");
        }

        return buildPage(run, exceptionTypes, severities, signals, confidences, qualityFlags,
                storeId, skuId, hasExecutableCandidate, page, size);
    }

    private Mvp2InventoryExceptionPage buildPage(
            SpAnalysisRun run,
            Set<InventoryExceptionType> exceptionTypes,
            Set<InventorySeverity> severities,
            Set<DemandSignalType> signals,
            Set<DemandConfidence> confidences,
            Set<MetricQualityFlag> qualityFlags,
            String storeId,
            String skuId,
            Boolean hasExecutableCandidate,
            int page,
            int size) {

        boolean exceptionTypeActive = exceptionTypes != null;
        boolean severityActive = severities != null;
        boolean signalActive = signals != null;
        boolean confidenceActive = confidences != null;
        boolean qualityFlagActive = qualityFlags != null;

        Set<InventoryExceptionType> exceptionTypesBind = exceptionTypeActive ? exceptionTypes : EnumSet.of(InventoryExceptionType.NORMAL);
        Set<InventorySeverity> severitiesBind = severityActive ? severities : EnumSet.of(InventorySeverity.CRITICAL);
        Set<DemandSignalType> signalsBind = signalActive ? signals : EnumSet.of(DemandSignalType.STABLE_REPEAT);
        Set<DemandConfidence> confidencesBind = confidenceActive ? confidences : EnumSet.of(DemandConfidence.HIGH);
        Set<MetricQualityFlag> qualityFlagsBind = qualityFlagActive ? qualityFlags : EnumSet.of(MetricQualityFlag.OOS_CENSORED);

        LocalDate priceDate = run.getAnalysisDate().minusDays(1);
        String inputVersion = run.getInputSnapshotVersion();
        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());

        List<Long> ids = metricRepository.findPagedIds(
                run.getAnalysisRunId(), exceptionTypeActive, exceptionTypesBind, severityActive, severitiesBind,
                signalActive, signalsBind, confidenceActive, confidencesBind, qualityFlagActive, qualityFlagsBind,
                storeId, skuId, hasExecutableCandidate, priceDate, inputVersion, pageable);
        long totalElements = metricRepository.countPaged(
                run.getAnalysisRunId(), exceptionTypeActive, exceptionTypesBind, severityActive, severitiesBind,
                signalActive, signalsBind, confidenceActive, confidencesBind, qualityFlagActive, qualityFlagsBind,
                storeId, skuId, hasExecutableCandidate);

        List<Mvp2InventoryExceptionListItem> items = ids.isEmpty()
                ? List.of()
                : buildListItems(ids, run, priceDate, inputVersion);

        int totalPages = size > 0 ? (int) Math.ceil(totalElements / (double) size) : 0;
        boolean hasNext = page < totalPages - 1;
        boolean hasPrevious = page > 0;

        return new Mvp2InventoryExceptionPage(
                run.getAnalysisRunId(), run.getAnalysisDate(), run.getInputSnapshotVersion(), run.getRuleVersion(),
                run.getCompletedAt(), ASSUMPTION_TYPE, ASSUMPTION_NOTICE, page, size, totalElements, totalPages,
                hasPrevious, hasNext, items);
    }

    private List<Mvp2InventoryExceptionListItem> buildListItems(
            List<Long> ids, SpAnalysisRun run, LocalDate priceDate, String inputVersion) {

        Map<Long, SpInventoryMetric> metricsById = new HashMap<>();
        Map<Long, SpProduct> productByMetric = new HashMap<>();
        Map<Long, SpStore> storeByMetric = new HashMap<>();
        Map<Long, List<MetricQualityFlag>> flagsByMetric = new HashMap<>();
        Map<Long, BigDecimal> priceByMetric = new HashMap<>();
        for (Object[] row : metricRepository.findListRowsByInventoryMetricIdIn(ids, priceDate, inputVersion)) {
            SpInventoryMetric m = (SpInventoryMetric) row[0];
            Long id = m.getInventoryMetricId();
            metricsById.putIfAbsent(id, m);
            productByMetric.putIfAbsent(id, (SpProduct) row[1]);
            storeByMetric.putIfAbsent(id, (SpStore) row[2]);
            if (row[3] != null) {
                flagsByMetric.computeIfAbsent(id, k -> new ArrayList<>()).add(((SpMetricQualityFlag) row[3]).getFlagCode());
            }
            priceByMetric.putIfAbsent(id, (BigDecimal) row[4]);
        }
        flagsByMetric.values().forEach(list -> list.sort(Comparator.naturalOrder()));

        Map<Long, int[]> candidateCounts = new HashMap<>();
        for (SpRebalanceRecommendation r : recommendationRepository.findByReceiverMetricIdInOrDonorMetricIdIn(ids)) {
            addCandidateCount(candidateCounts, r.getReceiverMetric().getInventoryMetricId(), r, ids);
            addCandidateCount(candidateCounts, r.getDonorMetric().getInventoryMetricId(), r, ids);
        }

        Set<String> storeIds = new java.util.HashSet<>();
        Set<String> skuIds = new java.util.HashSet<>();
        for (Long id : ids) {
            SpInventorySnapshot snap = metricsById.get(id).getInventorySnapshot();
            storeIds.add(snap.getStoreId());
            skuIds.add(snap.getSkuId());
        }

        OffsetDateTime cutoff = run.getAnalysisDate().atStartOfDay(ASSUMPTION_TIMEZONE).toOffsetDateTime();
        Map<StoreSkuKey, int[]> inboundQuantityByPair = new HashMap<>();
        Map<StoreSkuKey, OffsetDateTime> inboundNextEtaByPair = new HashMap<>();
        for (SpInboundSchedule inbound : inboundScheduleRepository.findConfirmedForListSummary(inputVersion, storeIds, skuIds, cutoff)) {
            StoreSkuKey key = new StoreSkuKey(inbound.getStoreId(), inbound.getSkuId());
            inboundQuantityByPair.computeIfAbsent(key, k -> new int[1])[0] += inbound.getQuantity();
            inboundNextEtaByPair.merge(key, inbound.getEtaAt(), (a, b) -> a.isBefore(b) ? a : b);
        }

        List<Mvp2InventoryExceptionListItem> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            SpInventoryMetric metric = metricsById.get(id);
            SpInventorySnapshot snap = metric.getInventorySnapshot();
            SpProduct product = productByMetric.get(id);
            SpStore store = storeByMetric.get(id);
            StoreSkuKey key = new StoreSkuKey(snap.getStoreId(), snap.getSkuId());
            BigDecimal price = priceByMetric.get(id);
            Long shortage = metric.getExpectedShortageQuantity();
            BigDecimal impact = (price != null && shortage != null)
                    ? price.multiply(BigDecimal.valueOf(shortage)).setScale(2, RoundingMode.HALF_UP)
                    : null;
            int[] counts = candidateCounts.getOrDefault(id, new int[3]);
            Integer upcomingQuantity = inboundQuantityByPair.containsKey(key) ? inboundQuantityByPair.get(key)[0] : null;
            OffsetDateTime nextEta = inboundNextEtaByPair.get(key);

            items.add(new Mvp2InventoryExceptionListItem(
                    metric.getInventoryMetricId(),
                    snap.getStoreId(), store == null ? null : store.getStoreName(), store == null ? null : store.getRegion(),
                    snap.getSkuId(), product == null ? null : product.getProductName(),
                    product == null ? null : product.getCategory(), product == null ? null : product.getColor(),
                    product == null ? null : product.getSizeName(),
                    metric.getClassification(), metric.getPriority(),
                    metric.getAvailableQuantity(), metric.getAverageDailySales(), metric.getCoverageDays(),
                    metric.getInventoryExceptionType(), metric.getSeverity(),
                    metric.getPrimaryDemandSignalType(), metric.getDemandConfidence(),
                    metric.getBaseDemandRate(), metric.getProjectedAvailable(), metric.getExpectedShortageQuantity(),
                    metric.getCalculationVersion(),
                    flagsByMetric.getOrDefault(id, List.of()),
                    upcomingQuantity, nextEta, price, impact,
                    counts[0], counts[1], counts[2], counts[0] > 0));
        }
        return items;
    }

    private static void addCandidateCount(Map<Long, int[]> counts, Long metricId, SpRebalanceRecommendation r, List<Long> ids) {
        if (!ids.contains(metricId)) {
            return;
        }
        int[] bucket = counts.computeIfAbsent(metricId, k -> new int[3]);
        if (r.getCandidateStatus() == CandidateStatus.ELIGIBLE && r.getRecommendationMode() == RecommendationMode.RECOMMENDED) {
            bucket[0]++;
        } else if (r.getCandidateStatus() == CandidateStatus.ELIGIBLE && r.getRecommendationMode() == RecommendationMode.COMPARISON_ONLY) {
            bucket[1]++;
        } else if (r.getCandidateStatus() == CandidateStatus.REJECTED) {
            bucket[2]++;
        }
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    /** Routes to the MVP-1 legacy shape or builds the MVP-2 shape, per current-task.md section 1.3. */
    public Object getExceptionDetail(Long inventoryMetricId) {
        SpInventoryMetric metric = metricRepository.findWithSnapshotAndRunById(inventoryMetricId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.INVENTORY_EXCEPTION_NOT_FOUND, "No inventory exception found for id " + inventoryMetricId));

        String ruleVersion = metric.getAnalysisRun().getRuleVersion();
        if (InventoryAnalysisRules.RULE_VERSION.equals(ruleVersion)) {
            return inventoryExceptionService.getExceptionDetail(inventoryMetricId);
        }
        if (!DemandAnalysisRules.RULE_VERSION.equals(ruleVersion)) {
            // Per the P2 finding: an explicit, known, non-MVP-2 rule version is required to route
            // to the MVP-1 legacy shape above -- an unrecognized value must never be silently
            // treated as MVP-1, since that would misrepresent whatever shape it actually holds.
            throw internalError(metric, "unrecognized rule version '" + ruleVersion + "' on run "
                    + metric.getAnalysisRun().getAnalysisRunId());
        }
        if (metric.getInventoryExceptionType() == null || metric.getInventoryExceptionType() == InventoryExceptionType.NORMAL) {
            throw new ApiException(ApiErrorCode.INVENTORY_EXCEPTION_NOT_FOUND, "No inventory exception found for id " + inventoryMetricId);
        }
        return buildDetail(metric);
    }

    private Mvp2InventoryExceptionDetail buildDetail(SpInventoryMetric metric) {
        SpAnalysisRun run = metric.getAnalysisRun();
        SpInventorySnapshot snapshot = metric.getInventorySnapshot();
        String storeId = snapshot.getStoreId();
        String skuId = snapshot.getSkuId();
        String inputVersion = run.getInputSnapshotVersion();

        // One statement for product+store+quality-flags together, per current-task.md section 5's
        // query ceiling -- reuses the same merged list-row source the list endpoint uses, since a
        // single-metric id set answers exactly the same three lookups this detail also needs.
        List<Object[]> catalogRows = metricRepository.findListRowsByInventoryMetricIdIn(
                List.of(metric.getInventoryMetricId()), run.getAnalysisDate().minusDays(1), inputVersion);
        if (catalogRows.isEmpty()) {
            throw catalogMissing(metric, "product/store", skuId + "/" + storeId);
        }
        SpProduct product = (SpProduct) catalogRows.get(0)[1];
        SpStore store = (SpStore) catalogRows.get(0)[2];
        List<MetricQualityFlag> flags = catalogRows.stream()
                .map(row -> (SpMetricQualityFlag) row[3])
                .filter(java.util.Objects::nonNull)
                .map(SpMetricQualityFlag::getFlagCode)
                .sorted(Comparator.naturalOrder())
                .toList();

        var runSummary = new Mvp2InventoryExceptionDetail.RunSummary(
                run.getAnalysisRunId(), run.getAnalysisDate(), run.getInputSnapshotVersion(), run.getRuleVersion(), run.getCompletedAt());
        var storeSummary = new Mvp2InventoryExceptionDetail.StoreSummary(store.getStoreId(), store.getStoreName(), store.getRegion());
        var productSummary = new Mvp2InventoryExceptionDetail.ProductSummary(
                product.getSkuId(), product.getProductName(), product.getCategory(), product.getColor(), product.getSizeName());
        var assumption = new Mvp2InventoryExceptionDetail.AssumptionNotice(ASSUMPTION_TYPE, ASSUMPTION_NOTICE);
        var metricDetail = new Mvp2InventoryExceptionDetail.MetricDetail(
                metric.getClassification(), metric.getPriority(), metric.getAvailableQuantity(),
                metric.getAverageDailySales(), metric.getCoverageDays(),
                metric.getInventoryExceptionType(), metric.getSeverity(),
                metric.getPrimaryDemandSignalType(), metric.getDemandConfidence(),
                metric.getProjectedAvailable(), metric.getExpectedShortageQuantity(), metric.getCalculationVersion(),
                metric.getObservableDayCount(), metric.getActiveWeekCount(), metric.getSalesDayRatio(),
                metric.getMaxDailySales(), metric.getMedianDailySales(), metric.getMadDailySales(), metric.getMaxTransactionQuantity(),
                metric.getLowDemandRate(), metric.getBaseDemandRate(), metric.getHighDemandRate(), flags);

        var currentSnapshot = new Mvp2InventoryExceptionDetail.CurrentSnapshot(
                snapshot.getSnapshotDate(), snapshot.getSnapshotAt(), snapshot.getOnHandQuantity(), snapshot.getReservedQuantity(),
                metric.getAvailableQuantity(), snapshot.isOutOfStock(), snapshot.getSourceType());

        var policy = buildPolicy(storeId, skuId, inputVersion);
        var observationWindow = buildObservationWindow(metric, run.getAnalysisDate(), storeId, skuId, inputVersion);

        List<Mvp2InventoryExceptionDetail.DemandEventView> demandEvents = demandEventRepository
                .findByStoreIdAndSkuIdAndInputSnapshotVersion(storeId, skuId, inputVersion).stream()
                .sorted(Comparator.comparing(SpDemandEvent::getStartDate).thenComparing(SpDemandEvent::getEventCode))
                .map(e -> new Mvp2InventoryExceptionDetail.DemandEventView(
                        e.getEventCode(), e.getEventType(), e.getStartDate(), e.getEndDate(),
                        e.getUpliftLow(), e.getUpliftBase(), e.getUpliftHigh(), e.getSourceType(), ASSUMPTION_TYPE))
                .toList();

        List<Mvp2InventoryExceptionDetail.InboundScheduleView> inboundSchedules = inboundScheduleRepository
                .findByStoreIdAndSkuIdAndInputSnapshotVersion(storeId, skuId, inputVersion).stream()
                .sorted(Comparator.comparing(SpInboundSchedule::getEtaAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SpInboundSchedule::getInboundReference))
                .map(s -> new Mvp2InventoryExceptionDetail.InboundScheduleView(
                        s.getInboundReference(), s.getQuantity(), s.getEtaAt(), s.getInboundStatus(), s.getSourceType()))
                .toList();

        List<Mvp2InventoryExceptionDetail.OpenTransferView> openTransfers = buildOpenTransfers(storeId, skuId, inputVersion);

        List<SpRebalanceRecommendation> recommendations = recommendationRepository.findByReceiverMetricIdOrDonorMetricId(metric.getInventoryMetricId());
        List<Long> recommendationIds = recommendations.stream().map(SpRebalanceRecommendation::getRecommendationId).toList();

        Map<Long, List<SpCandidateReason>> reasonsByRecommendation = new HashMap<>();
        if (!recommendationIds.isEmpty()) {
            candidateReasonRepository.findByRecommendation_RecommendationIdInOrderByReasonOrderAsc(recommendationIds)
                    .forEach(r -> reasonsByRecommendation.computeIfAbsent(r.getRecommendation().getRecommendationId(), k -> new ArrayList<>()).add(r));
        }
        Map<Long, List<SpRebalanceScenario>> scenariosByRecommendation = new HashMap<>();
        if (!recommendationIds.isEmpty()) {
            scenarioRepository.findByRecommendation_RecommendationIdInOrderByRecommendation_RecommendationIdAsc(recommendationIds)
                    .forEach(s -> scenariosByRecommendation.computeIfAbsent(s.getRecommendation().getRecommendationId(), k -> new ArrayList<>()).add(s));
            scenariosByRecommendation.values().forEach(list -> list.sort(Comparator.comparing(SpRebalanceScenario::getScenarioType)));
        }
        Map<Long, SpRebalanceDecision> latestDecisionByRecommendation = new HashMap<>();
        if (!recommendationIds.isEmpty()) {
            decisionRepository.findByRecommendation_RecommendationIdInOrderByRecommendation_RecommendationIdAscDecisionSequenceDesc(recommendationIds)
                    .forEach(d -> latestDecisionByRecommendation.putIfAbsent(d.getRecommendation().getRecommendationId(), d));
        }

        Map<Long, SpStoreTransferRoute> routesById = new HashMap<>();
        List<Long> routeIds = recommendations.stream().map(SpRebalanceRecommendation::getRouteId).filter(java.util.Objects::nonNull).toList();
        if (!routeIds.isEmpty()) {
            storeTransferRouteRepository.findAllById(routeIds).forEach(r -> routesById.put(r.getRouteId(), r));
        }

        Set<String> counterpartStoreIds = new java.util.HashSet<>();
        for (SpRebalanceRecommendation r : recommendations) {
            counterpartStoreIds.add(r.getReceiverMetric().getInventoryMetricId().equals(metric.getInventoryMetricId())
                    ? r.getDonorMetric().getInventorySnapshot().getStoreId()
                    : r.getReceiverMetric().getInventorySnapshot().getStoreId());
        }
        Map<String, SpStore> counterpartStores = new HashMap<>();
        storeRepository.findAllById(counterpartStoreIds).forEach(s -> counterpartStores.put(s.getStoreId(), s));

        List<Mvp2InventoryExceptionDetail.CandidateDetail> asReceiver = new ArrayList<>();
        List<Mvp2InventoryExceptionDetail.CandidateDetail> asDonor = new ArrayList<>();
        for (SpRebalanceRecommendation r : recommendations) {
            boolean isReceiver = r.getReceiverMetric().getInventoryMetricId().equals(metric.getInventoryMetricId());
            String counterpartStoreId = isReceiver
                    ? r.getDonorMetric().getInventorySnapshot().getStoreId()
                    : r.getReceiverMetric().getInventorySnapshot().getStoreId();
            SpStore counterpartStore = counterpartStores.get(counterpartStoreId);
            Mvp2InventoryExceptionDetail.RouteInfo route = null;
            if (r.getRouteId() != null) {
                SpStoreTransferRoute routeEntity = routesById.get(r.getRouteId());
                if (routeEntity == null) {
                    throw internalError(metric, "route " + r.getRouteId() + " referenced by recommendation "
                            + r.getRecommendationId() + " could not be found");
                }
                route = new Mvp2InventoryExceptionDetail.RouteInfo(
                        routeEntity.getRouteId(), routeEntity.isActive(), routeEntity.isOwnerOverride(),
                        routeEntity.getLeadTimeDays(), routeEntity.getMinimumQuantity(), routeEntity.getPackageMultiple(),
                        routeEntity.getMaximumQuantity(), ASSUMPTION_TYPE);
            }
            List<Mvp2InventoryExceptionDetail.RejectionReasonView> reasons = reasonsByRecommendation
                    .getOrDefault(r.getRecommendationId(), List.of()).stream()
                    .map(reason -> new Mvp2InventoryExceptionDetail.RejectionReasonView(reason.getReasonCode(), reason.getReasonOrder()))
                    .toList();
            List<Mvp2InventoryExceptionDetail.ScenarioView> scenarios = scenariosByRecommendation
                    .getOrDefault(r.getRecommendationId(), List.of()).stream()
                    .map(s -> new Mvp2InventoryExceptionDetail.ScenarioView(
                            s.getScenarioId(), s.getScenarioType(), s.getDemandRate(), s.getScenarioQuantity(), s.getPackageMultiple(),
                            s.getReceiverBeforeAvailable(), s.getReceiverAfterAvailable(), s.getReceiverBeforeCoverage(), s.getReceiverAfterCoverage(),
                            s.getReceiverRiskCode(), s.getDonorBeforeAvailable(), s.getDonorAfterAvailable(), s.getDonorBeforeCoverage(),
                            s.getDonorAfterCoverage(), s.getDonorRiskCode(), s.getLeadTimeDays(), s.getExpectedArrivalAt(),
                            s.isInboundIncluded(), s.getWarningSummary(), s.getCandidateVersion(), s.getCreatedAt()))
                    .toList();
            SpRebalanceDecision decision = latestDecisionByRecommendation.get(r.getRecommendationId());
            Mvp2InventoryExceptionDetail.LatestDecisionView latestDecision = decision == null ? null
                    : new Mvp2InventoryExceptionDetail.LatestDecisionView(
                            decision.getDecisionSequence(), decision.getDecisionStatus(), decision.getSelectedQuantity(),
                            decision.getReasonCode(), decision.getReason(), decision.getActorLabel(), decision.getDecidedAt());

            var candidate = new Mvp2InventoryExceptionDetail.CandidateDetail(
                    r.getRecommendationId(), isReceiver ? "RECEIVER" : "DONOR",
                    counterpartStoreId, counterpartStore == null ? null : counterpartStore.getStoreName(),
                    route, r.getCandidateStatus(), r.getCandidateVersion(), r.getRecommendationMode(),
                    r.getReceiverShortageQuantity(), r.getDonorTransferableQuantity(), r.getRecommendedQuantity(),
                    r.getProjectedReceiverAtArrival(), r.getProjectedDonorAtDispatch(), r.getReceiverCapacityRemaining(),
                    r.getEvaluatedAt(), reasons, scenarios, latestDecision);
            if (isReceiver) {
                asReceiver.add(candidate);
            } else {
                asDonor.add(candidate);
            }
        }
        asReceiver.sort(CANDIDATE_ORDER);
        asDonor.sort(CANDIDATE_ORDER);

        var ruleAssumptions = new Mvp2InventoryExceptionDetail.RuleAssumptions(
                DemandAnalysisRules.OBSERVATION_WINDOW_DAYS, DemandAnalysisRules.MINIMUM_OBSERVABLE_DAYS, DemandAnalysisRules.MINIMUM_LAUNCH_DAYS,
                DemandAnalysisRules.STABLE_REPEAT_MAX_WEEKLY_CV, DemandAnalysisRules.STABLE_REPEAT_MINIMUM_ACTIVE_WEEKS,
                DemandAnalysisRules.INTERMITTENT_MAXIMUM_ACTIVE_WEEKS, DemandAnalysisRules.INTERMITTENT_MAXIMUM_SALES_DAY_RATIO,
                DemandAnalysisRules.SPIKE_ABSOLUTE_MINIMUM, DemandAnalysisRules.SPIKE_MAD_MULTIPLIER, DemandAnalysisRules.SPIKE_WINDOW_SHARE_MINIMUM,
                DemandAnalysisRules.BULK_TRANSACTION_MINIMUM_QUANTITY, DemandAnalysisRules.BULK_TRANSACTION_SHARE_MINIMUM,
                DemandAnalysisRules.MINIMUM_VALID_WEEKLY_RATES, DemandAnalysisRules.LOW_DEMAND_RATE_PERCENTILE,
                DemandAnalysisRules.BASE_DEMAND_RATE_PERCENTILE, DemandAnalysisRules.HIGH_DEMAND_RATE_PERCENTILE, ASSUMPTION_TYPE);

        return new Mvp2InventoryExceptionDetail(
                runSummary, storeSummary, productSummary, assumption, metricDetail, currentSnapshot, policy,
                observationWindow, demandEvents, inboundSchedules, openTransfers, asReceiver, asDonor, ruleAssumptions);
    }

    private Mvp2InventoryExceptionDetail.PolicyInfo buildPolicy(String storeId, String skuId, String inputVersion) {
        return storeSkuPolicyRepository.findByStoreIdAndSkuIdAndInputSnapshotVersion(storeId, skuId, inputVersion)
                .map(p -> new Mvp2InventoryExceptionDetail.PolicyInfo(
                        "VERSIONED_INPUT", p.getDisplayMinimum(), p.getSafetyStock(), p.getMaximumCapacity(),
                        p.getTargetCoverageDays(), p.getRetainedDays(), ASSUMPTION_TYPE))
                .orElseGet(() -> new Mvp2InventoryExceptionDetail.PolicyInfo(
                        "DEFAULT_ASSUMPTION", DemandAnalysisRules.DEFAULT_DISPLAY_MINIMUM, DemandAnalysisRules.DEFAULT_SAFETY_STOCK,
                        DemandAnalysisRules.DEFAULT_MAXIMUM_CAPACITY, DemandAnalysisRules.DEFAULT_TARGET_COVERAGE_DAYS,
                        DemandAnalysisRules.DEFAULT_RETAINED_DAYS, ASSUMPTION_TYPE));
    }

    private Mvp2InventoryExceptionDetail.ObservationWindow buildObservationWindow(
            SpInventoryMetric metric, LocalDate analysisDate, String storeId, String skuId, String inputVersion) {
        LocalDate start = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate end = analysisDate.minusDays(1);

        List<SpInventorySnapshot> snapshots = inventorySnapshotRepository
                .findByStoreIdAndSkuIdAndSnapshotDateBetweenAndInputSnapshotVersion(storeId, skuId, start, end, inputVersion);
        List<SpDailySale> sales = dailySaleRepository
                .findByStoreIdAndSkuIdAndSalesDateBetweenAndInputSnapshotVersion(storeId, skuId, start, end, inputVersion);

        Map<LocalDate, SpInventorySnapshot> snapshotByDate = new HashMap<>();
        snapshots.forEach(s -> snapshotByDate.put(s.getSnapshotDate(), s));
        Map<LocalDate, SpDailySale> saleByDate = new HashMap<>();
        sales.forEach(s -> saleByDate.put(s.getSalesDate(), s));

        if (snapshotByDate.size() != DemandAnalysisRules.OBSERVATION_WINDOW_DAYS
                || saleByDate.size() != DemandAnalysisRules.OBSERVATION_WINDOW_DAYS) {
            throw internalError(metric, "observation window for " + storeId + "/" + skuId + "/" + inputVersion
                    + " did not have exactly " + DemandAnalysisRules.OBSERVATION_WINDOW_DAYS + " inventory/sales rows each");
        }

        List<Mvp2InventoryExceptionDetail.ObservationDay> days = new ArrayList<>(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            SpInventorySnapshot snap = snapshotByDate.get(d);
            SpDailySale sale = saleByDate.get(d);
            if (snap == null || sale == null) {
                throw internalError(metric, "observation window for " + storeId + "/" + skuId + "/" + inputVersion
                        + " is missing a row for " + d);
            }
            days.add(new Mvp2InventoryExceptionDetail.ObservationDay(
                    d, snap.getOnHandQuantity(), snap.getReservedQuantity(), snap.isOutOfStock(), snap.getSnapshotAt(),
                    sale.getSoldQuantity(), sale.getTransactionCount(), sale.getMaxTransactionQuantity(), sale.getAverageSellingPrice(),
                    snap.getSourceType(), sale.getSourceType()));
        }
        return new Mvp2InventoryExceptionDetail.ObservationWindow(start, end, DemandAnalysisRules.OBSERVATION_WINDOW_DAYS, days);
    }

    private List<Mvp2InventoryExceptionDetail.OpenTransferView> buildOpenTransfers(String storeId, String skuId, String inputVersion) {
        return openTransferRepository.findOpenForStore(storeId, skuId, inputVersion, OPEN_TRANSFER_STATUSES).stream()
                .map(t -> toOpenTransferView(t, storeId.equals(t.getReceiverStoreId()) ? "RECEIVER" : "DONOR"))
                .sorted(Comparator.comparing(Mvp2InventoryExceptionDetail.OpenTransferView::etaAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Mvp2InventoryExceptionDetail.OpenTransferView::transferReference))
                .toList();
    }

    private static Mvp2InventoryExceptionDetail.OpenTransferView toOpenTransferView(SpOpenTransfer t, String direction) {
        return new Mvp2InventoryExceptionDetail.OpenTransferView(
                t.getTransferReference(), direction, t.getDonorStoreId(), t.getReceiverStoreId(),
                t.getQuantity(), t.getEtaAt(), t.getTransferStatus(), t.getSourceType());
    }

    private ApiException catalogMissing(SpInventoryMetric metric, String kind, String id) {
        log.error("MVP-2 inventory exception detail for metric {} references a missing {} catalog row: {}",
                metric.getInventoryMetricId(), kind, id);
        return internalError(metric, kind + " catalog row " + id + " is missing");
    }

    private ApiException internalError(SpInventoryMetric metric, String detail) {
        log.error("MVP-2 inventory exception detail invariant violation for metric {}: {}", metric.getInventoryMetricId(), detail);
        return new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR, detail);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int recommendationModeRank(RecommendationMode mode) {
        return switch (mode) {
            case RECOMMENDED -> 0;
            case COMPARISON_ONLY -> 1;
            case NONE -> 2;
        };
    }

    private static <T extends Enum<T>> Set<T> parseEnumFilter(
            String fieldName, List<String> rawValues, Class<T> enumType, Set<T> allowedValues, List<ApiFieldError> fieldErrors) {
        if (rawValues == null || rawValues.isEmpty()) {
            return null;
        }
        Set<T> result = EnumSet.noneOf(enumType);
        for (String raw : rawValues) {
            T parsed = null;
            try {
                parsed = Enum.valueOf(enumType, raw);
            } catch (IllegalArgumentException ignored) {
                // handled below: parsed stays null, reported as a FORMAT error.
            }
            if (parsed == null || !allowedValues.contains(parsed)) {
                fieldErrors.add(new ApiFieldError(fieldName, "FORMAT", "허용되지 않는 " + fieldName + " 값입니다."));
            } else {
                result.add(parsed);
            }
        }
        return result;
    }

    private static String validateExactMatch(String fieldName, String raw, List<ApiFieldError> fieldErrors) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            fieldErrors.add(new ApiFieldError(fieldName, "REQUIRED", fieldName + "는 빈 문자열일 수 없습니다."));
            return null;
        }
        if (trimmed.length() > 64) {
            fieldErrors.add(new ApiFieldError(fieldName, "SIZE", fieldName + "는 64자 이하여야 합니다."));
            return null;
        }
        return trimmed;
    }

    private record StoreSkuKey(String storeId, String skuId) {
    }
}
