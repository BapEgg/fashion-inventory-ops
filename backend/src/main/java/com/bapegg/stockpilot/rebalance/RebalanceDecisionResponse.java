package com.bapegg.stockpilot.rebalance;

import java.time.OffsetDateTime;

public record RebalanceDecisionResponse(
        Long decisionId,
        Long recommendationId,
        DecisionStatus decisionStatus,
        Integer selectedQuantity,
        String reason,
        String actorLabel,
        OffsetDateTime decidedAt
) {

    static RebalanceDecisionResponse from(SpRebalanceDecision decision) {
        return new RebalanceDecisionResponse(
                decision.getDecisionId(),
                decision.getRecommendation().getRecommendationId(),
                decision.getDecisionStatus(),
                decision.getSelectedQuantity(),
                decision.getReason(),
                decision.getActorLabel(),
                decision.getDecidedAt());
    }
}
