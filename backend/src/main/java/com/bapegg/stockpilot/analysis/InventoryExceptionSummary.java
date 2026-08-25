package com.bapegg.stockpilot.analysis;

import java.math.BigDecimal;

public record InventoryExceptionSummary(
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
        Long recommendationId,
        Integer recommendedQuantity
) {
}
