package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one terminal approval or rejection for a recommendation, per
 * business-rules.md section 6: a recommendation gets at most one decision, and the selected
 * quantity must fall within the same valid-simulation range used by
 * {@code POST /api/rebalancing-simulations} (1..donorTransferableQuantity) -- the only rule left
 * here that genuinely needs the recommendation loaded first. {@code selectedQuantity}/{@code
 * reason} required-ness/non-blankness is a pure field-shape check with no DB dependency, so per
 * the Codex review's P2 finding {@code RebalanceDecisionController} validates it itself, before
 * this method (and any repository access) ever runs -- this method trusts that precondition and
 * no longer re-checks it.
 * <p>
 * The receiver metric's lazy {@code analysisRun} association -- needed for the MVP-1-only guard
 * below -- is loaded within this method's own write transaction, same idea as the read-only guard
 * {@link RebalanceSimulationService#simulate} uses for its own legacy path. Every failure is a
 * catalog-backed {@link ApiException} (never a raw {@code ResponseStatusException}) so
 * {@code RebalanceDecisionController} can share {@code AnalysisApiExceptionHandler}'s advice
 * scope without this MVP-1 flow's errors being misclassified under an unrelated analysis code.
 * A save-time {@link org.springframework.dao.DataIntegrityViolationException} (e.g. two
 * concurrent legacy requests racing {@link SpRebalanceDecisionRepository#existsByRecommendation_RecommendationId}
 * with no lock, both passing it before either commits) is left to propagate raw -- the caller
 * translates it once this method's transaction has fully unwound, mirroring why
 * {@code ApprovalTransactionExecutor} never translates a constraint violation inside its own
 * still-open transaction either.
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
            Long recommendationId, DecisionStatus decisionStatus, Integer selectedQuantity, String reason,
            String actorLabel) {
        if (decisionStatus != DecisionStatus.APPROVED && decisionStatus != DecisionStatus.REJECTED) {
            // DecisionStatus now covers every MVP-2 persistence state so the JPA mapping can
            // read them, but this MVP-1 flow has no concept of PENDING/HELD/EXPIRED -- widening
            // the enum for persistence must not silently widen what this REST contract accepts.
            throw new ApiException(ApprovalErrorCode.INVALID_DECISION_REQUEST,
                    "decisionStatus must be APPROVED or REJECTED.");
        }

        SpRebalanceRecommendation recommendation = recommendationRepository.findWithMetricsById(recommendationId)
                .orElseThrow(() -> new ApiException(
                        ApprovalErrorCode.RECOMMENDATION_NOT_FOUND, "No recommendation found for id " + recommendationId));

        // Per current-task.md section 1.2: a tuple-less/header-less request only ever runs this
        // legacy (unlocked, un-revalidated) writer for a recommendation whose run's rule version
        // is *exactly* the MVP-1 identifier -- an allowlist, not a "not MVP-2" denylist, so an
        // unknown, future or MVP-2-family rule version is rejected too, not silently accepted.
        String receiverRuleVersion = recommendation.getReceiverMetric().getAnalysisRun().getRuleVersion();
        if (!InventoryAnalysisRules.RULE_VERSION.equals(receiverRuleVersion)) {
            throw new ApiException(ApprovalErrorCode.INVALID_DECISION_REQUEST,
                    "Recommendation " + recommendationId + " is not an MVP-1 recommendation; "
                            + "the MVP-2 version tuple and Idempotency-Key are required to decide it.");
        }

        if (decisionRepository.existsByRecommendation_RecommendationId(recommendationId)) {
            throw new ApiException(ApprovalErrorCode.DECISION_ALREADY_TERMINAL,
                    "Recommendation " + recommendationId + " already has a terminal decision.");
        }

        // selectedQuantity is guaranteed non-null and positive here -- RebalanceDecisionController
        // already rejected any other shape as VALIDATION_ERROR before calling this method.
        if (selectedQuantity > recommendation.getDonorTransferableQuantity()) {
            throw new ApiException(ApprovalErrorCode.INVALID_DECISION_REQUEST,
                    "selectedQuantity must be between 1 and " + recommendation.getDonorTransferableQuantity()
                            + " (donorTransferableQuantity) to have a valid simulation.");
        }

        SpRebalanceDecision decision = decisionRepository.save(
                new SpRebalanceDecision(recommendation, decisionStatus, selectedQuantity, reason, actorLabel));
        return RebalanceDecisionResponse.from(decision);
    }
}
