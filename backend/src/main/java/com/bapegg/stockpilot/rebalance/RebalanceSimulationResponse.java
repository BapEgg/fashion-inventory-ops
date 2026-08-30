package com.bapegg.stockpilot.rebalance;

public record RebalanceSimulationResponse(
        Long recommendationId,
        Integer requestedQuantity,
        StoreCoverage receiverBefore,
        StoreCoverage receiverAfter,
        StoreCoverage donorBefore,
        StoreCoverage donorAfter
) {
}
