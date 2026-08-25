package com.bapegg.stockpilot.analysis;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AnalysisRunRequest(@NotNull LocalDate analysisDate) {
}
