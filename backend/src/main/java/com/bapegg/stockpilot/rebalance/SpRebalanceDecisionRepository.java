package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpRebalanceDecisionRepository extends JpaRepository<SpRebalanceDecision, Long> {

    /**
     * "Does this recommendation already have a decision" for the tuple-less legacy branch's
     * at-most-one-decision rule. Deliberately {@code exists}, not a single-result
     * {@code find}: {@code V6} dropped the unique constraint on {@code recommendation_id}
     * alone, so now that {@code ApprovalTransactionExecutor} is a real append-only writer, a
     * single-result query here would throw {@code IncorrectResultSizeDataAccessException} the
     * moment a second decision is written for the same recommendation (the MVP-2 branch's
     * HELD-then-APPROVED history, for example).
     */
    boolean existsByRecommendation_RecommendationId(Long recommendationId);

    /**
     * The current decision for a recommendation, defined as the row with the highest
     * {@code decision_sequence} ({@code V6}'s append-only
     * {@code uq_sp_dec_rec_seq (recommendation_id, decision_sequence)}). Safe to call now that
     * {@code ApprovalTransactionExecutor} is a real multi-decision writer, unlike a
     * single-result {@code findBy...} would be.
     */
    Optional<SpRebalanceDecision> findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc(
            Long recommendationId);

    /**
     * The full append-only {@code recommendation_id -> decision_sequence} history
     * ({@code V6}'s {@code uq_sp_dec_rec_seq}), used by both
     * {@code ApprovalTransactionExecutor} (to compute the next sequence under the
     * recommendation row's lock) and {@code Mvp2DecisionHistoryQueryService} (the ordered
     * {@code GET /api/rebalancing-decisions/{recommendationId}} response). The MVP-1
     * constructor still always inserts {@code decision_sequence = 1} -- a legacy
     * recommendation's history is at most one row -- but a real MVP-2 recommendation's
     * history can be several, HELD then APPROVED for example.
     */
    List<SpRebalanceDecision> findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(Long recommendationId);

    /**
     * The idempotency-key winner lookup for the approval transaction. Safe as a
     * single-result query: {@code V10}'s {@code uq_sp_dec_request_id} guarantees at most
     * one row per {@code decision_request_id}.
     */
    Optional<SpRebalanceDecision> findByDecisionRequestId(String decisionRequestId);

    /**
     * The MVP-2 detail read API's bulk current-decision lookup for every candidate on one
     * metric, per current-task.md section 4.6 -- avoids one query per candidate. Callers reduce
     * each {@code recommendation_id} group to its highest {@code decision_sequence} row (already
     * guaranteed first per group by this ordering).
     */
    List<SpRebalanceDecision> findByRecommendation_RecommendationIdInOrderByRecommendation_RecommendationIdAscDecisionSequenceDesc(
            Collection<Long> recommendationIds);
}
