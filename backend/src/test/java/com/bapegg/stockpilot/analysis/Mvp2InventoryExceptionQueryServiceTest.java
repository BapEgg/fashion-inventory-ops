package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.api.error.ApiFieldError;
import com.bapegg.stockpilot.catalog.SpProduct;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.inventory.SpDailySaleRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import com.bapegg.stockpilot.rebalance.SpCandidateReasonRepository;
import com.bapegg.stockpilot.rebalance.SpDemandEventRepository;
import com.bapegg.stockpilot.rebalance.SpInboundScheduleRepository;
import com.bapegg.stockpilot.rebalance.SpOpenTransferRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenarioRepository;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link Mvp2InventoryExceptionQueryService}'s request validation and
 * MVP-1/MVP-2 detail routing, per current-task.md section 7.1. No Spring context, no Oracle --
 * every repository is mocked. The DB-computed list/detail body mapping itself (bulk queries,
 * fixed sort, 28-day evidence assembly) is covered by the Oracle Golden Scenario IT instead,
 * since it depends on real filter/order/aggregate SQL this suite deliberately does not
 * re-implement in Java.
 */
class Mvp2InventoryExceptionQueryServiceTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP2-QUERYSVC-V1";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;

    private final SpAnalysisRunRepository analysisRunRepository = mock(SpAnalysisRunRepository.class);
    private final SpInventoryMetricRepository metricRepository = mock(SpInventoryMetricRepository.class);
    private final SpRebalanceRecommendationRepository recommendationRepository = mock(SpRebalanceRecommendationRepository.class);
    private final SpCandidateReasonRepository candidateReasonRepository = mock(SpCandidateReasonRepository.class);
    private final SpRebalanceScenarioRepository scenarioRepository = mock(SpRebalanceScenarioRepository.class);
    private final SpRebalanceDecisionRepository decisionRepository = mock(SpRebalanceDecisionRepository.class);
    private final SpStoreTransferRouteRepository storeTransferRouteRepository = mock(SpStoreTransferRouteRepository.class);
    private final SpStoreSkuPolicyRepository storeSkuPolicyRepository = mock(SpStoreSkuPolicyRepository.class);
    private final SpDemandEventRepository demandEventRepository = mock(SpDemandEventRepository.class);
    private final SpInboundScheduleRepository inboundScheduleRepository = mock(SpInboundScheduleRepository.class);
    private final SpOpenTransferRepository openTransferRepository = mock(SpOpenTransferRepository.class);
    private final SpDailySaleRepository dailySaleRepository = mock(SpDailySaleRepository.class);
    private final SpInventorySnapshotRepository inventorySnapshotRepository = mock(SpInventorySnapshotRepository.class);
    private final SpStoreRepository storeRepository = mock(SpStoreRepository.class);
    private final InventoryExceptionService inventoryExceptionService = mock(InventoryExceptionService.class);

    private final Mvp2InventoryExceptionQueryService service = new Mvp2InventoryExceptionQueryService(
            analysisRunRepository, metricRepository, recommendationRepository,
            candidateReasonRepository, scenarioRepository, decisionRepository, storeTransferRouteRepository,
            storeSkuPolicyRepository, demandEventRepository, inboundScheduleRepository, openTransferRepository,
            dailySaleRepository, inventorySnapshotRepository, storeRepository,
            inventoryExceptionService);

    // ------------------------------------------------------------------
    // List validation
    // ------------------------------------------------------------------

    @Test
    void bothAnalysisDateAndAnalysisRunIdIsRejectedAsForbidden() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                ANALYSIS_DATE, 1L, null, null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("analysisDate") && f.code().equals("FORBIDDEN")));
    }

    @Test
    void aRunBoundParameterWithNoAnalysisRunIdIsRejectedAsRequired() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, null, null, null, null, null, null, null, null, null, 1, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("analysisRunId") && f.code().equals("REQUIRED")));
    }

    @Test
    void aNonPositiveAnalysisRunIdIsRejectedAsFormat() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 0L, null, null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("analysisRunId") && f.code().equals("FORMAT")));
    }

    @Test
    void anUnknownExceptionTypeValueIsRejectedAsFormat() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, List.of("NORMAL"), null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("exceptionType") && f.code().equals("FORMAT")));
    }

    @Test
    void aMalformedSeverityValueIsRejectedAsFormat() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, null, List.of("not-a-severity"), null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("severity") && f.code().equals("FORMAT")));
        assertTrue(e.fieldErrors().stream().noneMatch(f -> f.message().contains("not-a-severity")));
    }

    @Test
    void aBlankStoreIdIsRejectedAsRequired() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, null, null, null, null, null, "   ", null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("storeId") && f.code().equals("REQUIRED")));
    }

    @Test
    void anOverlongSkuIdIsRejectedAsSize() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, null, null, null, null, null, null, "S".repeat(65), null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("skuId") && f.code().equals("SIZE")));
    }

    @Test
    void aNegativePageIsRejectedAsFormat() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, null, null, null, null, null, null, null, null, -1, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("page") && f.code().equals("FORMAT")));
    }

    @Test
    void aSizeAboveOneHundredIsRejectedAsFormat() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 1L, null, null, null, null, null, null, null, null, null, 101));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("size") && f.code().equals("FORMAT")));
    }

    @Test
    void multipleSimultaneousValidationFailuresAreAllReported() {
        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, -1L, List.of("bogus"), null, null, null, null, "", null, null, -1, 999));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        List<String> fields = e.fieldErrors().stream().map(ApiFieldError::field).toList();
        assertTrue(fields.contains("analysisRunId"));
        assertTrue(fields.contains("exceptionType"));
        assertTrue(fields.contains("storeId"));
        assertTrue(fields.contains("page"));
        assertTrue(fields.contains("size"));
    }

    // ------------------------------------------------------------------
    // List run resolution
    // ------------------------------------------------------------------

    @Test
    void anUnknownAnalysisRunIdIsAnalysisNotFound() {
        when(analysisRunRepository.findById(999L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 999L, null, null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.ANALYSIS_NOT_FOUND, e.code());
    }

    @Test
    void anMvp1RuleVersionRunIdIsRejectedAsFormat() {
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, "MVP-1");
        when(analysisRunRepository.findById(5L)).thenReturn(Optional.of(run));

        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 5L, null, null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().equals("analysisRunId") && f.code().equals("FORMAT")));
    }

    @Test
    void aNotYetCompletedMvp2RunIsResultsNotReady() {
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION);
        when(analysisRunRepository.findById(7L)).thenReturn(Optional.of(run));

        ApiException e = assertThrows(ApiException.class, () -> service.listExceptions(
                null, 7L, null, null, null, null, null, null, null, null, null, null));

        assertEquals(ApiErrorCode.ANALYSIS_RESULTS_NOT_READY, e.code());
    }

    // ------------------------------------------------------------------
    // Detail routing
    // ------------------------------------------------------------------

    @Test
    void anUnknownMetricIdIsInventoryExceptionNotFound() {
        when(metricRepository.findWithSnapshotAndRunById(123L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class, () -> service.getExceptionDetail(123L));

        assertEquals(ApiErrorCode.INVENTORY_EXCEPTION_NOT_FOUND, e.code());
    }

    @Test
    void anMvp1MetricDelegatesToTheLegacyService() {
        SpInventoryMetric metric = mock(SpInventoryMetric.class);
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, "MVP-1");
        when(metric.getAnalysisRun()).thenReturn(run);
        when(metricRepository.findWithSnapshotAndRunById(42L)).thenReturn(Optional.of(metric));
        InventoryExceptionDetail legacyResult = new InventoryExceptionDetail(
                42L, "SKU-1", "Product", "STORE-1", "Store", InventoryClassification.STOCKOUT_RISK,
                InventoryPriority.HIGH, 5, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, List.of(), List.of());
        when(inventoryExceptionService.getExceptionDetail(42L)).thenReturn(legacyResult);

        Object result = service.getExceptionDetail(42L);

        assertEquals(legacyResult, result);
        verify(inventoryExceptionService).getExceptionDetail(42L);
    }

    @Test
    void anMvp2NormalMetricIsInventoryExceptionNotFound() {
        SpInventoryMetric metric = mock(SpInventoryMetric.class);
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION);
        when(metric.getAnalysisRun()).thenReturn(run);
        when(metric.getInventoryExceptionType()).thenReturn(InventoryExceptionType.NORMAL);
        when(metricRepository.findWithSnapshotAndRunById(77L)).thenReturn(Optional.of(metric));

        ApiException e = assertThrows(ApiException.class, () -> service.getExceptionDetail(77L));

        assertEquals(ApiErrorCode.INVENTORY_EXCEPTION_NOT_FOUND, e.code());
        verify(inventoryExceptionService, never()).getExceptionDetail(anyLong());
    }

    @Test
    void anMvp2MetricWithNoExceptionTypeIsInventoryExceptionNotFound() {
        SpInventoryMetric metric = mock(SpInventoryMetric.class);
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION);
        when(metric.getAnalysisRun()).thenReturn(run);
        when(metric.getInventoryExceptionType()).thenReturn(null);
        when(metricRepository.findWithSnapshotAndRunById(78L)).thenReturn(Optional.of(metric));

        ApiException e = assertThrows(ApiException.class, () -> service.getExceptionDetail(78L));

        assertEquals(ApiErrorCode.INVENTORY_EXCEPTION_NOT_FOUND, e.code());
    }

    /**
     * Per the P2 finding: an unrecognized rule version (neither {@code InventoryAnalysisRules
     * .RULE_VERSION} nor {@code DemandAnalysisRules.RULE_VERSION}) must never be silently routed
     * to the MVP-1 legacy shape just because it isn't MVP-2.
     */
    @Test
    void anUnrecognizedRuleVersionIsAnInternalError() {
        SpInventoryMetric metric = mock(SpInventoryMetric.class);
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, "MVP-3-UNKNOWN");
        when(metric.getAnalysisRun()).thenReturn(run);
        when(metric.getInventoryMetricId()).thenReturn(79L);
        when(metricRepository.findWithSnapshotAndRunById(79L)).thenReturn(Optional.of(metric));

        ApiException e = assertThrows(ApiException.class, () -> service.getExceptionDetail(79L));

        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
        verify(inventoryExceptionService, never()).getExceptionDetail(anyLong());
    }

    // ------------------------------------------------------------------
    // List happy path (mapping/aggregation)
    // ------------------------------------------------------------------

    @Test
    void aRunBoundListMapsCandidateCountsFlagsAndEstimatedSalesImpact() {
        Long runId = 900L;
        String storeId = "STORE-X";
        String skuId = "SKU-X";
        LocalDate analysisDate = LocalDate.of(2026, 10, 1);

        SpAnalysisRun run = mock(SpAnalysisRun.class);
        when(run.getAnalysisRunId()).thenReturn(runId);
        when(run.getRuleVersion()).thenReturn(RULE_VERSION);
        when(run.getRunStatus()).thenReturn(AnalysisRunStatus.COMPLETED);
        when(run.getAnalysisDate()).thenReturn(analysisDate);
        when(run.getInputSnapshotVersion()).thenReturn(INPUT_SNAPSHOT_VERSION);
        when(run.getCompletedAt()).thenReturn(OffsetDateTime.now());
        when(analysisRunRepository.findById(runId)).thenReturn(Optional.of(run));

        SpInventorySnapshot snapshot = mock(SpInventorySnapshot.class);
        when(snapshot.getStoreId()).thenReturn(storeId);
        when(snapshot.getSkuId()).thenReturn(skuId);

        SpInventoryMetric metric = mock(SpInventoryMetric.class);
        when(metric.getInventoryMetricId()).thenReturn(100L);
        when(metric.getInventorySnapshot()).thenReturn(snapshot);
        when(metric.getExpectedShortageQuantity()).thenReturn(5L);

        SpProduct product = mock(SpProduct.class);
        when(product.getSkuId()).thenReturn(skuId);
        SpStore store = mock(SpStore.class);
        when(store.getStoreId()).thenReturn(storeId);

        SpMetricQualityFlag flag = mock(SpMetricQualityFlag.class);
        when(flag.getInventoryMetric()).thenReturn(metric);
        when(flag.getFlagCode()).thenReturn(MetricQualityFlag.OOS_CENSORED);

        when(metricRepository.findPagedIds(eq(runId),
                anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(100L));
        when(metricRepository.countPaged(eq(runId),
                anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(),
                any(), any(), any()))
                .thenReturn(1L);
        when(metricRepository.findListRowsByInventoryMetricIdIn(
                eq(List.of(100L)), eq(analysisDate.minusDays(1)), eq(INPUT_SNAPSHOT_VERSION)))
                .thenReturn(java.util.Collections.singletonList(
                        new Object[]{metric, product, store, flag, new BigDecimal("10.00")}));

        SpInventoryMetric otherSideMetric = mock(SpInventoryMetric.class);
        when(otherSideMetric.getInventoryMetricId()).thenReturn(200L);
        SpRebalanceRecommendation recommendation = mock(SpRebalanceRecommendation.class);
        when(recommendation.getReceiverMetric()).thenReturn(metric);
        when(recommendation.getDonorMetric()).thenReturn(otherSideMetric);
        when(recommendation.getCandidateStatus()).thenReturn(CandidateStatus.ELIGIBLE);
        when(recommendation.getRecommendationMode()).thenReturn(RecommendationMode.RECOMMENDED);
        when(recommendationRepository.findByReceiverMetricIdInOrDonorMetricIdIn(List.of(100L)))
                .thenReturn(List.of(recommendation));

        when(inboundScheduleRepository.findConfirmedForListSummary(eq(INPUT_SNAPSHOT_VERSION), any(), any(), any()))
                .thenReturn(List.of());

        Mvp2InventoryExceptionPage page = service.listExceptions(
                null, runId, null, null, null, null, null, null, null, null, null, null);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.items().size());
        Mvp2InventoryExceptionListItem item = page.items().get(0);
        assertEquals(100L, item.inventoryMetricId());
        assertTrue(item.hasExecutableCandidate());
        assertEquals(1, item.executableCandidateCount());
        assertEquals(0, item.comparisonOnlyCandidateCount());
        assertEquals(0, item.rejectedCandidateCount());
        assertEquals(List.of(MetricQualityFlag.OOS_CENSORED), item.qualityFlags());
        assertEquals(0, new BigDecimal("10.00").compareTo(item.currentSellingPrice()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.estimatedSalesImpact()));
    }
}
