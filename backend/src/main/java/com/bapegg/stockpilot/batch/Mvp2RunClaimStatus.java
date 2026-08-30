package com.bapegg.stockpilot.batch;

/**
 * The outcome of one {@code (analysisDate, inputSnapshotVersion, ruleVersion)} run claim, per
 * current-task.md section 3's state table.
 */
public enum Mvp2RunClaimStatus {
    STARTED,
    ALREADY_RUNNING,
    ALREADY_COMPLETED
}
