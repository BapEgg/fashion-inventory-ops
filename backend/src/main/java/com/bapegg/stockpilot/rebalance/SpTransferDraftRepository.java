package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpTransferDraftRepository extends JpaRepository<SpTransferDraft, Long> {

    Optional<SpTransferDraft> findByDecision_DecisionId(Long decisionId);

    /**
     * The MVP-2 decision-history GET's bulk draft lookup for every decision in one
     * recommendation's history, per current-task.md section 4.5 -- one statement regardless of
     * history length. Every mapped field is a plain column, so no join/fetch is needed here.
     */
    List<SpTransferDraft> findByDecision_DecisionIdIn(Collection<Long> decisionIds);

    /**
     * The donor's already-approved active draft quantity, per business-rules.md section
     * 10: summed across every {@code input_snapshot_version} (an earlier approval's
     * commitment against shared donor supply stays real even if a newer analysis
     * snapshot has since been produced), not just the requested one.
     */
    @Query("""
            SELECT COALESCE(SUM(d.quantity), 0) FROM SpTransferDraft d
            WHERE d.donorStoreId = :donorStoreId AND d.skuId = :skuId
            AND d.draftStatus IN :activeStatuses AND d.decision.decisionStatus = :approvedStatus
            """)
    int sumActiveQuantityByDonorStoreIdAndSkuId(
            @Param("donorStoreId") String donorStoreId,
            @Param("skuId") String skuId,
            @Param("activeStatuses") Collection<DraftStatus> activeStatuses,
            @Param("approvedStatus") DecisionStatus approvedStatus);
}
