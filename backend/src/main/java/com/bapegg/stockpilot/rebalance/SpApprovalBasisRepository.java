package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpApprovalBasisRepository extends JpaRepository<SpApprovalBasis, Long> {

    Optional<SpApprovalBasis> findByDecision_DecisionId(Long decisionId);

    /**
     * The MVP-2 decision-history GET's bulk basis lookup for every decision in one
     * recommendation's history, per current-task.md section 4.5 -- one statement regardless of
     * history length. {@code JOIN FETCH} the (otherwise lazy) {@code analysisRun} association so
     * a caller reading only its id for {@code Mvp2ApprovalBasisItem.analysisRunId} never triggers
     * a per-row N+1 select.
     */
    @Query("SELECT b FROM SpApprovalBasis b JOIN FETCH b.analysisRun WHERE b.decision.decisionId IN :decisionIds")
    List<SpApprovalBasis> findWithAnalysisRunByDecisionIdIn(@Param("decisionIds") Collection<Long> decisionIds);
}
