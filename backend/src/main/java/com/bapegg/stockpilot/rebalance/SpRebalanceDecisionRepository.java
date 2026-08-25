package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpRebalanceDecisionRepository extends JpaRepository<SpRebalanceDecision, Long> {

    Optional<SpRebalanceDecision> findByRecommendation_RecommendationId(Long recommendationId);
}
