package com.bapegg.stockpilot.batch;

/** {@link Mvp2AnalysisExecutor#execute}'s result: which run id, and whether it was already complete. */
public record Mvp2ExecutionOutcome(Long runId, boolean alreadyCompleted) {
}
