package com.bapegg.stockpilot.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Validated
public class AnalysisController {

    private final AnalysisRunService analysisRunService;
    private final Mvp2AnalysisApplicationService mvp2AnalysisApplicationService;
    private final AnalysisRunQueryService analysisRunQueryService;

    public AnalysisController(
            AnalysisRunService analysisRunService,
            Mvp2AnalysisApplicationService mvp2AnalysisApplicationService,
            AnalysisRunQueryService analysisRunQueryService) {
        this.analysisRunService = analysisRunService;
        this.mvp2AnalysisApplicationService = mvp2AnalysisApplicationService;
        this.analysisRunQueryService = analysisRunQueryService;
    }

    @PostMapping("/api/analyses")
    public ResponseEntity<AnalysisRunResponse> startAnalysis(@Valid @RequestBody AnalysisRunRequest request) {
        if (request.inputSnapshotVersion() == null) {
            AnalysisRunService.AnalysisRunOutcome outcome = analysisRunService.runAnalysis(request.analysisDate());
            AnalysisRunResponse body = AnalysisRunResponse.from(outcome);
            return ResponseEntity.ok()
                    .location(locationOf(body.analysisRunId()))
                    .body(body);
        }

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                mvp2AnalysisApplicationService.launch(request.analysisDate(), request.inputSnapshotVersion());
        AnalysisRunResponse body = AnalysisRunResponse.fromMvp2(outcome);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(locationOf(body.analysisRunId()))
                .body(body);
    }

    @GetMapping("/api/analyses/{analysisRunId}")
    public AnalysisRunStatusResponse getAnalysisStatus(@PathVariable @Positive Long analysisRunId) {
        return analysisRunQueryService.getStatus(analysisRunId);
    }

    private static URI locationOf(Long analysisRunId) {
        return URI.create("/api/analyses/" + analysisRunId);
    }
}
