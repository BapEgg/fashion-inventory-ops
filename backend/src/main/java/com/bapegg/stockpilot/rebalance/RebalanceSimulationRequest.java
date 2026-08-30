package com.bapegg.stockpilot.rebalance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The legacy two-field MVP-1 shape stays required and unconditional. The four MVP-2 fields below
 * are additive and optional at the Bean Validation layer -- {@link RebalanceSimulationController}
 * enforces the "all four or none" cross-field rule itself, per current-task.md section 2, since
 * that rule cannot be expressed as a single-field constraint.
 */
public record RebalanceSimulationRequest(
        @NotNull Long recommendationId,
        @NotNull @Min(1) Integer requestedQuantity,
        Long analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        Integer candidateVersion
) {
}
