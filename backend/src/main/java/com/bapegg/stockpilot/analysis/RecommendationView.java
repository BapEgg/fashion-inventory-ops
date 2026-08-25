package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.rebalance.DecisionStatus;

public record RecommendationView(
        Long recommendationId,
        String counterpartStoreId,
        String counterpartStoreName,
        Integer receiverShortageQuantity,
        Integer donorTransferableQuantity,
        Integer recommendedQuantity,
        DecisionStatus decisionStatus,
        Integer decidedQuantity
) {
}
