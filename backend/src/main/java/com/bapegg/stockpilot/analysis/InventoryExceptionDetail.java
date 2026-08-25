package com.bapegg.stockpilot.analysis;

import java.math.BigDecimal;
import java.util.List;

public record InventoryExceptionDetail(
        Long inventoryMetricId,
        String skuId,
        String productName,
        String storeId,
        String storeName,
        InventoryClassification classification,
        InventoryPriority priority,
        Integer availableQuantity,
        BigDecimal averageDailySales,
        BigDecimal coverageDays,
        List<RecommendationView> recommendationsAsReceiver,
        List<RecommendationView> recommendationsAsDonor
) {
}
