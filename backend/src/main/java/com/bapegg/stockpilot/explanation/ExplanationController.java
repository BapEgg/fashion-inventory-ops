package com.bapegg.stockpilot.explanation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExplanationController {

    private final ExplanationService explanationService;

    public ExplanationController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @PostMapping("/api/inventory-exceptions/{id}/explanation")
    public ExplanationResponse explain(@PathVariable Long id) {
        return explanationService.explain(id);
    }
}
