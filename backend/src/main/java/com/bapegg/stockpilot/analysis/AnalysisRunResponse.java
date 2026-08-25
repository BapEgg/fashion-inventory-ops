package com.bapegg.stockpilot.analysis;

import java.time.LocalDate;

public record AnalysisRunResponse(
        Long analysisRunId,
        LocalDate analysisDate,
        String ruleVersion,
        AnalysisRunStatus status,
        boolean alreadyCompleted
) {

    static AnalysisRunResponse from(AnalysisRunService.AnalysisRunOutcome outcome) {
        SpAnalysisRun run = outcome.analysisRun();
        return new AnalysisRunResponse(
                run.getAnalysisRunId(),
                run.getAnalysisDate(),
                run.getRuleVersion(),
                run.getRunStatus(),
                outcome.alreadyCompleted());
    }
}
