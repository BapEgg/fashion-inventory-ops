package com.bapegg.stockpilot.rebalance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persists one terminal approval or rejection for a recommendation, per
 * business-rules.md section 6: a recommendation gets at most one decision, the reason
 * and actor label must be non-blank (also enforced at the DB and DTO validation
 * layers), and the selected quantity must fall within the same valid-simulation range
 * used by {@code POST /api/rebalancing-simulations} (1..donorTransferableQuantity).
 */
@Service
public class RebalanceDecisionService {

    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpRebalanceDecisionRepository decisionRepository;

    public RebalanceDecisionService(
            SpRebalanceRecommendationRepository recommendationRepository,
            SpRebalanceDecisionRepository decisionRepository) {
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional
    public RebalanceDecisionResponse decide(
            Long recommendationId, DecisionStatus decisionStatus, int selectedQuantity, String reason, String actorLabel) {
        SpRebalanceRecommendation recommendation = recommendationRepository.findWithMetricsById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No recommendation found for id " + recommendationId));

        if (decisionRepository.findByRecommendation_RecommendationId(recommendationId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Recommendation " + recommendationId + " already has a terminal decision.");
        }

        if (selectedQuantity < 1 || selectedQuantity > recommendation.getDonorTransferableQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "selectedQuantity must be between 1 and " + recommendation.getDonorTransferableQuantity()
                            + " (donorTransferableQuantity) to have a valid simulation.");
        }

        SpRebalanceDecision decision = decisionRepository.save(
                new SpRebalanceDecision(recommendation, decisionStatus, selectedQuantity, reason, actorLabel));
        return RebalanceDecisionResponse.from(decision);
    }
}
