package com.bapegg.stockpilot.rebalance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RebalanceDecisionRequest(
        @NotNull Long recommendationId,
        @NotNull DecisionStatus decisionStatus,
        @NotNull @Min(1) Integer selectedQuantity,
        @NotBlank String reason,
        @NotBlank String actorLabel
) {
}
