package com.bapegg.stockpilot.analysis;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
public class InventoryExceptionController {

    private final InventoryExceptionService inventoryExceptionService;
    private final Mvp2InventoryExceptionQueryService mvp2InventoryExceptionQueryService;

    public InventoryExceptionController(
            InventoryExceptionService inventoryExceptionService,
            Mvp2InventoryExceptionQueryService mvp2InventoryExceptionQueryService) {
        this.inventoryExceptionService = inventoryExceptionService;
        this.mvp2InventoryExceptionQueryService = mvp2InventoryExceptionQueryService;
    }

    /**
     * Legacy MVP-1 bare-array mode (no {@code analysisRunId} and none of the run-bound
     * parameters below) is unchanged. Any of those parameters routes to the new MVP-2
     * run-bound, paged read model, per current-task.md section 1.
     */
    @GetMapping("/api/inventory-exceptions")
    public Object listExceptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate analysisDate,
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false, name = "exceptionType") List<String> exceptionType,
            @RequestParam(required = false, name = "severity") List<String> severity,
            @RequestParam(required = false, name = "signal") List<String> signal,
            @RequestParam(required = false, name = "confidence") List<String> confidence,
            @RequestParam(required = false, name = "qualityFlag") List<String> qualityFlag,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String skuId,
            @RequestParam(required = false) Boolean hasExecutableCandidate,
            @RequestParam(required = false, name = "workStatus") List<String> workStatus,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        boolean runBoundParameterPresent = analysisRunId != null
                || exceptionType != null || severity != null || signal != null || confidence != null
                || qualityFlag != null || storeId != null || skuId != null
                || hasExecutableCandidate != null || workStatus != null || sortBy != null || sortDirection != null
                || page != null || size != null;

        if (!runBoundParameterPresent) {
            return inventoryExceptionService.listExceptions(Optional.ofNullable(analysisDate));
        }

        return mvp2InventoryExceptionQueryService.listExceptions(
                analysisDate, analysisRunId, exceptionType, severity, signal, confidence, qualityFlag,
                storeId, skuId, hasExecutableCandidate, workStatus, sortBy, sortDirection, page, size);
    }

    /**
     * Path identity is still {@code inventoryMetricId} (the parameter name below is
     * {@code metricId}, per current-task.md section 1.3). The response shape branches on the
     * metric's own run rule version -- see {@link Mvp2InventoryExceptionQueryService#getExceptionDetail}.
     */
    @GetMapping("/api/inventory-exceptions/{metricId}")
    public Object getException(@PathVariable Long metricId) {
        return mvp2InventoryExceptionQueryService.getExceptionDetail(metricId);
    }
}
