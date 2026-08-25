package com.bapegg.stockpilot.rebalance;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RebalanceDecisionController {

    private final RebalanceDecisionService rebalanceDecisionService;

    public RebalanceDecisionController(RebalanceDecisionService rebalanceDecisionService) {
        this.rebalanceDecisionService = rebalanceDecisionService;
    }

    @PostMapping("/api/rebalancing-decisions")
    public ResponseEntity<RebalanceDecisionResponse> decide(@Valid @RequestBody RebalanceDecisionRequest request) {
        RebalanceDecisionResponse response = rebalanceDecisionService.decide(
                request.recommendationId(),
                request.decisionStatus(),
                request.selectedQuantity(),
                request.reason(),
                request.actorLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
