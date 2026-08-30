package com.bapegg.stockpilot.analysis;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

import java.time.LocalDate;

/**
 * {@code POST /api/analyses}'s request contract, per current-task.md section 1: an absent/null
 * {@code inputSnapshotVersion} selects the existing MVP-1 path unchanged; a non-null value selects
 * MVP-2. {@code ruleVersion} is deliberately {@code @Null} -- the server always supplies the rule
 * version for whichever path is selected, so a client that sends one is rejected rather than
 * silently ignored. {@code inputSnapshotVersion}'s own blank/whitespace/length rules are enforced
 * once, by {@link com.bapegg.stockpilot.batch.Mvp2AnalysisJobParameters} itself, rather than
 * duplicated here.
 */
public record AnalysisRunRequest(
        @NotNull(message = "analysisDate는 필수입니다.") LocalDate analysisDate,
        String inputSnapshotVersion,
        @Null(message = "ruleVersion은 클라이언트가 지정할 수 없습니다.") String ruleVersion
) {
}
