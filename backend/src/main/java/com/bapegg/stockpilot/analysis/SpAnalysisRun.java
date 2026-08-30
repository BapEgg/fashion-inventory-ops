package com.bapegg.stockpilot.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Idempotent Batch analysis boundary identified by analysis date and rule version.
 * <p>
 * {@code inputSnapshotVersion} was added by {@code V6} for MVP-2's versioned inputs; the
 * MVP-1 constructor defaults it to {@value #LEGACY_INPUT_SNAPSHOT_VERSION}, matching the
 * same literal {@code V6} used to backfill every pre-existing row and as this column's
 * DB {@code DEFAULT}, so the existing MVP-1 create path is unchanged.
 */
@Entity
@Table(name = "sp_analysis_run")
public class SpAnalysisRun {

    private static final String LEGACY_INPUT_SNAPSHOT_VERSION = "MVP-1-LEGACY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_run_id")
    private Long analysisRunId;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false, length = 20)
    private AnalysisRunStatus runStatus;

    @Column(name = "started_at", insertable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    protected SpAnalysisRun() {
    }

    public SpAnalysisRun(LocalDate analysisDate, String ruleVersion) {
        this(analysisDate, ruleVersion, LEGACY_INPUT_SNAPSHOT_VERSION);
    }

    /**
     * For an MVP-2 run against a real (non-legacy) versioned input snapshot -- the plain
     * 2-arg constructor above is for the MVP-1 Batch path, which has no such concept.
     */
    public SpAnalysisRun(LocalDate analysisDate, String ruleVersion, String inputSnapshotVersion) {
        this.analysisDate = analysisDate;
        this.ruleVersion = ruleVersion;
        this.runStatus = AnalysisRunStatus.RUNNING;
        this.inputSnapshotVersion = inputSnapshotVersion;
    }

    /**
     * Phase 3 output-persistence run-claim state table (current-task.md section 3):
     * {@code restart}/{@code markFailed}/{@code markCompleted} only work from the specific
     * allowed prior state ({@code FAILED}, {@code RUNNING}, {@code RUNNING} respectively) -- a
     * completed run is never reverted to failed or running.
     */
    public void restart() {
        if (runStatus != AnalysisRunStatus.FAILED) {
            throw new IllegalStateException("restart() is only allowed from FAILED (was " + runStatus + ").");
        }
        this.runStatus = AnalysisRunStatus.RUNNING;
        this.completedAt = null;
    }

    public void markFailed() {
        if (runStatus != AnalysisRunStatus.RUNNING) {
            throw new IllegalStateException("markFailed() is only allowed from RUNNING (was " + runStatus + ").");
        }
        this.runStatus = AnalysisRunStatus.FAILED;
    }

    public void markCompleted() {
        if (runStatus != AnalysisRunStatus.RUNNING) {
            throw new IllegalStateException("markCompleted() is only allowed from RUNNING (was " + runStatus + ").");
        }
        this.runStatus = AnalysisRunStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public Long getAnalysisRunId() {
        return analysisRunId;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public AnalysisRunStatus getRunStatus() {
        return runStatus;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }
}
