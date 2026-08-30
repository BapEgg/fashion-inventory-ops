package com.bapegg.stockpilot.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AnalysisRunResponse(
        Long analysisRunId,
        LocalDate analysisDate,
        String ruleVersion,
        AnalysisRunStatus status,
        boolean alreadyCompleted,
        String inputSnapshotVersion,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {

    static AnalysisRunResponse from(AnalysisRunService.AnalysisRunOutcome outcome) {
        return of(outcome.analysisRun(), outcome.alreadyCompleted());
    }

    static AnalysisRunResponse fromMvp2(Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome) {
        return of(outcome.run(), outcome.alreadyCompleted());
    }

    private static AnalysisRunResponse of(SpAnalysisRun run, boolean alreadyCompleted) {
        return new AnalysisRunResponse(
                run.getAnalysisRunId(),
                run.getAnalysisDate(),
                run.getRuleVersion(),
                run.getRunStatus(),
                alreadyCompleted,
                run.getInputSnapshotVersion(),
                run.getStartedAt(),
                run.getCompletedAt());
    }
}
