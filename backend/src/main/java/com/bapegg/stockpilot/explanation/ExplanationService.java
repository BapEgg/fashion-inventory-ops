package com.bapegg.stockpilot.explanation;

import com.bapegg.stockpilot.analysis.InventoryExceptionService;
import org.springframework.stereotype.Service;

/**
 * The AI boundary (business-rules.md section 7): AI may only explain already-calculated
 * facts, never decide a quantity or status. When disabled or unconfigured, this reports
 * an explicit unavailable state instead of an error, so the deterministic API stays fully
 * usable without an LLM provider (project.md section 8).
 */
@Service
public class ExplanationService {

    private final InventoryExceptionService inventoryExceptionService;
    private final AiProperties aiProperties;

    public ExplanationService(InventoryExceptionService inventoryExceptionService, AiProperties aiProperties) {
        this.inventoryExceptionService = inventoryExceptionService;
        this.aiProperties = aiProperties;
    }

    public ExplanationResponse explain(Long inventoryMetricId) {
        // Validates the id is a real, actionable exception (same 404 rule the detail
        // endpoint uses) before reporting on AI availability for it.
        inventoryExceptionService.getExceptionDetail(inventoryMetricId);

        if (!aiProperties.enabled()) {
            return ExplanationResponse.unavailable("AI_DISABLED");
        }
        if (!aiProperties.fullyConfigured()) {
            return ExplanationResponse.unavailable("AI_UNCONFIGURED");
        }
        // No provider adapter exists yet; one is added only once real provider settings
        // are supplied and an adapter is built against them (AGENTS.md AI boundary).
        return ExplanationResponse.unavailable("AI_PROVIDER_NOT_IMPLEMENTED");
    }
}
