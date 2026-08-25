package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.catalog.SpStore;
import com.bapegg.stockpilot.catalog.SpStoreRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Compares a proposed transfer quantity for an existing recommendation without
 * persisting anything, per business-rules.md section 4 (Simulation).
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

    public RebalanceSimulationResponse simulate(Long recommendationId, int requestedQuantity) {
        SpRebalanceRecommendation recommendation = recommendationRepository.findWithMetricsById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No recommendation found for id " + recommendationId));

        if (requestedQuantity < 1 || requestedQuantity > recommendation.getDonorTransferableQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "requestedQuantity must be between 1 and " + recommendation.getDonorTransferableQuantity()
                            + " (donorTransferableQuantity).");
        }

        SpInventoryMetric receiverMetric = recommendation.getReceiverMetric();
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
