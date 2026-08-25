package com.bapegg.stockpilot.rebalance;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RebalanceSimulationController {

    private final RebalanceSimulationService rebalanceSimulationService;

    public RebalanceSimulationController(RebalanceSimulationService rebalanceSimulationService) {
        this.rebalanceSimulationService = rebalanceSimulationService;
    }

    @PostMapping("/api/rebalancing-simulations")
    public RebalanceSimulationResponse simulate(@Valid @RequestBody RebalanceSimulationRequest request) {
        return rebalanceSimulationService.simulate(request.recommendationId(), request.requestedQuantity());
    }
}
