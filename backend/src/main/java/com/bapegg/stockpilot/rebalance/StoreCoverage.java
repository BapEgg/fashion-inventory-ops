package com.bapegg.stockpilot.rebalance;

import java.math.BigDecimal;

/** {@code coverageDays} is {@code null} when unlimited or undefined, per business-rules.md section 2. */
public record StoreCoverage(
        String storeId,
        String storeName,
        Integer availableQuantity,
        BigDecimal coverageDays
) {
}
