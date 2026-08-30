package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Compares a proposed transfer quantity for an existing recommendation without
 * persisting anything, per business-rules.md section 4 (Simulation). This is the legacy MVP-1
 * path only -- a non-null version tuple on the request routes to the MVP-2 {@code MANUAL}
 * quantity-test executor instead, in {@link RebalanceSimulationController}.
 */
@Service
public class RebalanceSimulationService {

    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpStoreRepository storeRepository;

    public RebalanceSimulationService(
            SpRebalanceRecommendationRepository recommendationRepository, SpStoreRepository storeRepository) {
        this.recommendationRepository = recommendationRepository;
        this.storeRepository = storeRepository;
    }

    /**
     * Read-only so the receiver metric's lazy {@code analysisRun} association -- needed for the
     * MVP-1-only guard below -- can be loaded without a separate query or a
     * {@code LazyInitializationException} under this project's {@code open-in-view=false}.
     */
    @Transactional(readOnly = true)
    public RebalanceSimulationResponse simulate(Long recommendationId, int requestedQuantity) {
        SpRebalanceRecommendation recommendation = recommendationRepository.findWithMetricsById(recommendationId)
                .orElseThrow(() -> new ApiException(
                        ApprovalErrorCode.RECOMMENDATION_NOT_FOUND, "No recommendation found for id " + recommendationId));

        SpInventoryMetric receiverMetric = recommendation.getReceiverMetric();

        // Per current-task.md section 2: a tuple-less request only ever runs this legacy
        // (unlocked, un-revalidated) calculation for a recommendation whose run's rule version is
        // *exactly* the MVP-1 identifier -- an allowlist, not a "not MVP-2" denylist, so an
        // unknown, future or MVP-2-family rule version is rejected too, not silently accepted.
        if (!InventoryAnalysisRules.RULE_VERSION.equals(receiverMetric.getAnalysisRun().getRuleVersion())) {
            throw new ApiException(ApprovalErrorCode.INVALID_REQUEST,
                    "Recommendation " + recommendationId + " is not an MVP-1 recommendation; "
                            + "the MVP-2 version tuple is required to simulate it.");
        }

        if (requestedQuantity < 1 || requestedQuantity > recommendation.getDonorTransferableQuantity()) {
            throw new ApiException(ApprovalErrorCode.INVALID_REQUEST,
                    "requestedQuantity must be between 1 and " + recommendation.getDonorTransferableQuantity()
                            + " (donorTransferableQuantity).");
        }

        SpInventoryMetric donorMetric = recommendation.getDonorMetric();

        StoreCoverage receiverBefore = coverageOf(receiverMetric, receiverMetric.getAvailableQuantity());
        StoreCoverage receiverAfter = coverageOf(receiverMetric, receiverMetric.getAvailableQuantity() + requestedQuantity);
        StoreCoverage donorBefore = coverageOf(donorMetric, donorMetric.getAvailableQuantity());
        StoreCoverage donorAfter = coverageOf(donorMetric, donorMetric.getAvailableQuantity() - requestedQuantity);

        return new RebalanceSimulationResponse(
                recommendationId, requestedQuantity, receiverBefore, receiverAfter, donorBefore, donorAfter);
    }

    private StoreCoverage coverageOf(SpInventoryMetric metric, int availableQuantity) {
        SpInventorySnapshot snapshot = metric.getInventorySnapshot();
        SpStore store = storeRepository.findById(snapshot.getStoreId()).orElse(null);
        return new StoreCoverage(
                snapshot.getStoreId(),
                store == null ? null : store.getStoreName(),
                availableQuantity,
                coverageDays(availableQuantity, metric.getAverageDailySales()));
    }

    /**
     * Same three-way rule as business-rules.md section 2: normal division when average
     * daily sales is positive; {@code null} (unlimited or undefined) otherwise.
     * Kept as a small local duplicate of the formula already reviewed and tested in
     * {@code InventoryMetricCalculation} rather than refactoring that class for this.
     */
    private static BigDecimal coverageDays(int availableQuantity, BigDecimal averageDailySales) {
        if (averageDailySales.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(availableQuantity).divide(averageDailySales, 2, RoundingMode.HALF_UP);
    }
}
