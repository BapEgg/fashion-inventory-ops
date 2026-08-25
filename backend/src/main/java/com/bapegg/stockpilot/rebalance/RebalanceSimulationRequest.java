package com.bapegg.stockpilot.rebalance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RebalanceSimulationRequest(
        @NotNull Long recommendationId,
        @NotNull @Min(1) Integer requestedQuantity
) {
}
