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
 */
@Entity
@Table(name = "sp_analysis_run")
public class SpAnalysisRun {

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

    protected SpAnalysisRun() {
    }

    public SpAnalysisRun(LocalDate analysisDate, String ruleVersion) {
        this.analysisDate = analysisDate;
        this.ruleVersion = ruleVersion;
        this.runStatus = AnalysisRunStatus.RUNNING;
    }

    public void markCompleted() {
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
}
