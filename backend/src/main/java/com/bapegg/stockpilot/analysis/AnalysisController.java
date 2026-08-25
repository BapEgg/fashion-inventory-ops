package com.bapegg.stockpilot.analysis;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisController {

    private final AnalysisRunService analysisRunService;

    public AnalysisController(AnalysisRunService analysisRunService) {
        this.analysisRunService = analysisRunService;
    }

    @PostMapping("/api/analyses")
    public AnalysisRunResponse startAnalysis(@Valid @RequestBody AnalysisRunRequest request) {
        AnalysisRunService.AnalysisRunOutcome outcome = analysisRunService.runAnalysis(request.analysisDate());
        return AnalysisRunResponse.from(outcome);
    }
}
