package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpCandidateReasonRepository extends JpaRepository<SpCandidateReason, Long> {

    /**
     * Bulk rejection-reason lookup for a detail response's receiver+donor candidate set, per
     * current-task.md section 4.6 -- {@code reasonOrder} ascending within each recommendation.
     */
    List<SpCandidateReason> findByRecommendation_RecommendationIdInOrderByReasonOrderAsc(
            Collection<Long> recommendationIds);
}
