package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpRebalanceScenarioRepository extends JpaRepository<SpRebalanceScenario, Long> {

    List<SpRebalanceScenario> findByRecommendation_RecommendationIdOrderByScenarioType(Long recommendationId);

    /**
     * The MVP-2 detail read API's bulk scenario lookup for every candidate on one metric, per
     * current-task.md section 4.6 -- avoids one query per candidate. Callers group the result
     * by {@code recommendation_id} in memory and must still order each group by
     * {@code scenarioType} themselves (this ordering is only guaranteed across recommendations,
     * not within one).
     */
    List<SpRebalanceScenario> findByRecommendation_RecommendationIdInOrderByRecommendation_RecommendationIdAsc(
            Collection<Long> recommendationIds);
}
