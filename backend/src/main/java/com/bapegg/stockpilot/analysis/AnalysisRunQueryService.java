package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * {@code GET /api/analyses/{analysisRunId}}'s read model, per current-task.md section 1: any
 * domain run (MVP-1 or MVP-2) by id, mapping nothing beyond what {@link SpAnalysisRun} already
 * stored -- Batch metadata and failure detail are never exposed here.
 */
@Service
public class AnalysisRunQueryService {

    private final SpAnalysisRunRepository analysisRunRepository;

    public AnalysisRunQueryService(SpAnalysisRunRepository analysisRunRepository) {
        this.analysisRunRepository = analysisRunRepository;
    }

    public AnalysisRunStatusResponse getStatus(Long analysisRunId) {
        SpAnalysisRun run = analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ANALYSIS_NOT_FOUND,
                        "No analysis run found for id " + analysisRunId + "."));
        return AnalysisRunStatusResponse.from(run);
    }
}
