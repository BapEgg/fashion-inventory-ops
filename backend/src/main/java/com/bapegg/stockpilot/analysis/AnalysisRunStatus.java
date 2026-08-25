package com.bapegg.stockpilot.analysis;

/**
 * Matches the {@code ck_sp_run_status} check constraint on {@code sp_analysis_run.run_status}.
 */
public enum AnalysisRunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
