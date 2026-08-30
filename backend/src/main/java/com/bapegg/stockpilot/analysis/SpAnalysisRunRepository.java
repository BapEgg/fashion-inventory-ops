package com.bapegg.stockpilot.analysis;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SpAnalysisRunRepository extends JpaRepository<SpAnalysisRun, Long> {

    Optional<SpAnalysisRun> findByAnalysisDateAndRuleVersion(LocalDate analysisDate, String ruleVersion);

    /**
     * The Phase 3 output-persistence run claim's only write lock, per current-task.md section 3:
     * no {@code jakarta.persistence.lock.timeout} query hint here (unlike the approval
     * transaction's locks) -- there is deliberately no 3-second demo timeout on this lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM SpAnalysisRun r
            WHERE r.analysisDate = :analysisDate AND r.inputSnapshotVersion = :inputSnapshotVersion
                AND r.ruleVersion = :ruleVersion
            """)
    Optional<SpAnalysisRun> lockByNaturalKey(
            @Param("analysisDate") LocalDate analysisDate,
            @Param("inputSnapshotVersion") String inputSnapshotVersion,
            @Param("ruleVersion") String ruleVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SpAnalysisRun r WHERE r.analysisRunId = :analysisRunId")
    Optional<SpAnalysisRun> lockById(@Param("analysisRunId") Long analysisRunId);

    /**
     * The MVP-2 Batch job's triple identifying-parameter lookup, per
     * {@code data-model.md} section 8: the same {@code COMPLETED} triple is a no-op, a
     * {@code FAILED} triple is restarted in place, and a different {@code inputSnapshotVersion}
     * is always a new run.
     */
    Optional<SpAnalysisRun> findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
            LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion);

    Optional<SpAnalysisRun> findTopByRuleVersionAndRunStatusOrderByAnalysisDateDesc(
            String ruleVersion, AnalysisRunStatus runStatus);

    /**
     * The approval transaction's "is this recommendation's run still the current one for
     * this rule version" check, per business-rules.md section 10: the full
     * {@code (analysis_date DESC, completed_at DESC, analysis_run_id DESC)} tie-break,
     * not just the single-column ordering {@link #findTopByRuleVersionAndRunStatusOrderByAnalysisDateDesc} uses.
     */
    Optional<SpAnalysisRun> findFirstByRuleVersionAndRunStatusOrderByAnalysisDateDescCompletedAtDescAnalysisRunIdDesc(
            String ruleVersion, AnalysisRunStatus runStatus);
}
