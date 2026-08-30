package com.bapegg.stockpilot.batch;

/** The result of one run claim attempt: which run id, and whether the caller must now run the pipeline. */
public record Mvp2RunClaim(Long runId, Mvp2RunClaimStatus status) {
}
