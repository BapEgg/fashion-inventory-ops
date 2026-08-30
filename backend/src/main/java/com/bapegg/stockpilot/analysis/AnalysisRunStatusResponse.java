package com.bapegg.stockpilot.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AnalysisRunStatusResponse(
        Long analysisRunId,
        LocalDate analysisDate,
        String inputSnapshotVersion,
        String ruleVersion,
        AnalysisRunStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {

    static AnalysisRunStatusResponse from(SpAnalysisRun run) {
        return new AnalysisRunStatusResponse(
                run.getAnalysisRunId(),
                run.getAnalysisDate(),
                run.getInputSnapshotVersion(),
                run.getRuleVersion(),
                run.getRunStatus(),
                run.getStartedAt(),
                run.getCompletedAt());
    }
}
