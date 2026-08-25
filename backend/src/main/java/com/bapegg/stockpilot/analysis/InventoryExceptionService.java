package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.catalog.SpProduct;
import com.bapegg.stockpilot.catalog.SpProductRepository;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only queries over one analysis run's actionable exceptions
 * (stockout risk and overstock; not normal or non-actionable), per project.md's
 * "exception list" and "detail" API surface.
 */
@Service
public class InventoryExceptionService {

    private static final Set<InventoryClassification> ACTIONABLE = Set.of(
            InventoryClassification.STOCKOUT_RISK, InventoryClassification.OVERSTOCK);

    private static final Comparator<SpInventoryMetric> EXCEPTION_ORDER = Comparator
            .comparing((SpInventoryMetric m) -> m.getClassification() == InventoryClassification.STOCKOUT_RISK ? 0 : 1)
            .thenComparing(m -> priorityRank(m.getPriority()))
            .thenComparing(SpInventoryMetric::getCoverageDays, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SpAnalysisRunRepository analysisRunRepository;
    private final SpInventoryMetricRepository metricRepository;
    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpRebalanceDecisionRepository decisionRepository;
    private final SpProductRepository productRepository;
    private final SpStoreRepository storeRepository;

    public InventoryExceptionService(
            SpAnalysisRunRepository analysisRunRepository,
            SpInventoryMetricRepository metricRepository,
            SpRebalanceRecommendationRepository recommendationRepository,
            SpRebalanceDecisionRepository decisionRepository,
            SpProductRepository productRepository,
            SpStoreRepository storeRepository) {
        this.analysisRunRepository = analysisRunRepository;
        this.metricRepository = metricRepository;
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    public List<InventoryExceptionSummary> listExceptions(Optional<LocalDate> analysisDate) {
        SpAnalysisRun run = resolveRun(analysisDate);

        List<SpInventoryMetric> metrics = metricRepository.findByAnalysisRun_AnalysisRunId(run.getAnalysisRunId())
                .stream()
                .filter(m -> ACTIONABLE.contains(m.getClassification()))
                .sorted(EXCEPTION_ORDER)
                .toList();

        Map<String, SpProduct> products = loadProducts(metrics.stream().map(m -> m.getInventorySnapshot().getSkuId()));
        Map<String, SpStore> stores = loadStores(metrics.stream().map(m -> m.getInventorySnapshot().getStoreId()));

        Map<Long, SpRebalanceRecommendation> recommendationByReceiverMetricId =
                recommendationRepository.findByReceiverMetric_AnalysisRun_AnalysisRunId(run.getAnalysisRunId())
                        .stream()
                        .collect(Collectors.toMap(
                                r -> r.getReceiverMetric().getInventoryMetricId(), r -> r, (a, b) -> a));

        return metrics.stream()
                .map(metric -> toSummary(metric, products, stores, recommendationByReceiverMetricId))
                .toList();
    }

    public InventoryExceptionDetail getExceptionDetail(Long inventoryMetricId) {
        SpInventoryMetric metric = metricRepository.findWithSnapshotById(inventoryMetricId)
                .filter(m -> ACTIONABLE.contains(m.getClassification()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No inventory exception found for id " + inventoryMetricId));

        SpInventorySnapshot snapshot = metric.getInventorySnapshot();
        SpProduct product = productRepository.findById(snapshot.getSkuId()).orElse(null);
        SpStore store = storeRepository.findById(snapshot.getStoreId()).orElse(null);

        List<SpRebalanceRecommendation> recommendations =
                recommendationRepository.findByReceiverMetricIdOrDonorMetricId(inventoryMetricId);

        Set<String> counterpartStoreIds = recommendations.stream()
                .map(r -> r.getReceiverMetric().getInventoryMetricId().equals(inventoryMetricId)
                        ? r.getDonorMetric().getInventorySnapshot().getStoreId()
                        : r.getReceiverMetric().getInventorySnapshot().getStoreId())
                .collect(Collectors.toSet());
        Map<String, SpStore> counterpartStores = loadStores(counterpartStoreIds.stream());

        List<RecommendationView> asReceiver = recommendations.stream()
                .filter(r -> r.getReceiverMetric().getInventoryMetricId().equals(inventoryMetricId))
                .map(r -> toRecommendationView(r, r.getDonorMetric().getInventorySnapshot().getStoreId(), counterpartStores))
                .toList();
        List<RecommendationView> asDonor = recommendations.stream()
                .filter(r -> r.getDonorMetric().getInventoryMetricId().equals(inventoryMetricId))
                .map(r -> toRecommendationView(r, r.getReceiverMetric().getInventorySnapshot().getStoreId(), counterpartStores))
                .toList();

        return new InventoryExceptionDetail(
                metric.getInventoryMetricId(),
                snapshot.getSkuId(),
                product == null ? null : product.getProductName(),
                snapshot.getStoreId(),
                store == null ? null : store.getStoreName(),
                metric.getClassification(),
                metric.getPriority(),
                metric.getAvailableQuantity(),
                metric.getAverageDailySales(),
                metric.getCoverageDays(),
                asReceiver,
                asDonor);
    }

    private SpAnalysisRun resolveRun(Optional<LocalDate> analysisDate) {
        if (analysisDate.isPresent()) {
            return analysisRunRepository
                    .findByAnalysisDateAndRuleVersion(analysisDate.get(), InventoryAnalysisRules.RULE_VERSION)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "No analysis run found for date " + analysisDate.get()));
        }
        return analysisRunRepository
                .findTopByRuleVersionAndRunStatusOrderByAnalysisDateDesc(
                        InventoryAnalysisRules.RULE_VERSION, AnalysisRunStatus.COMPLETED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No completed analysis run found."));
    }

    private RecommendationView toRecommendationView(
            SpRebalanceRecommendation recommendation, String counterpartStoreId, Map<String, SpStore> counterpartStores) {
        SpStore counterpartStore = counterpartStores.get(counterpartStoreId);
        var decision = decisionRepository.findByRecommendation_RecommendationId(recommendation.getRecommendationId());
        return new RecommendationView(
                recommendation.getRecommendationId(),
                counterpartStoreId,
                counterpartStore == null ? null : counterpartStore.getStoreName(),
                recommendation.getReceiverShortageQuantity(),
                recommendation.getDonorTransferableQuantity(),
                recommendation.getRecommendedQuantity(),
                decision.map(d -> d.getDecisionStatus()).orElse(null),
                decision.map(d -> d.getSelectedQuantity()).orElse(null));
    }

    private InventoryExceptionSummary toSummary(
            SpInventoryMetric metric,
            Map<String, SpProduct> products,
            Map<String, SpStore> stores,
            Map<Long, SpRebalanceRecommendation> recommendationByReceiverMetricId) {
        SpInventorySnapshot snapshot = metric.getInventorySnapshot();
        SpProduct product = products.get(snapshot.getSkuId());
        SpStore store = stores.get(snapshot.getStoreId());
        SpRebalanceRecommendation recommendation = recommendationByReceiverMetricId.get(metric.getInventoryMetricId());

        return new InventoryExceptionSummary(
                metric.getInventoryMetricId(),
                snapshot.getSkuId(),
                product == null ? null : product.getProductName(),
                snapshot.getStoreId(),
                store == null ? null : store.getStoreName(),
                metric.getClassification(),
                metric.getPriority(),
                metric.getAvailableQuantity(),
                metric.getAverageDailySales(),
                metric.getCoverageDays(),
                recommendation == null ? null : recommendation.getRecommendationId(),
                recommendation == null ? null : recommendation.getRecommendedQuantity());
    }

    private Map<String, SpProduct> loadProducts(Stream<String> skuIds) {
        Map<String, SpProduct> result = new HashMap<>();
        productRepository.findAllById(skuIds.distinct().toList()).forEach(p -> result.put(p.getSkuId(), p));
        return result;
    }

    private Map<String, SpStore> loadStores(Stream<String> storeIds) {
        Map<String, SpStore> result = new HashMap<>();
        storeRepository.findAllById(storeIds.distinct().toList()).forEach(s -> result.put(s.getStoreId(), s));
        return result;
    }

    private static int priorityRank(InventoryPriority priority) {
        if (priority == InventoryPriority.CRITICAL) {
            return 0;
        }
        if (priority == InventoryPriority.HIGH) {
            return 1;
        }
        return 2;
    }
}
