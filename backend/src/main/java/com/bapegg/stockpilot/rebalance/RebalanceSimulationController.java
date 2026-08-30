package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import com.bapegg.stockpilot.approval.ApprovalTransactionException;
import com.bapegg.stockpilot.approval.ManualQuantityTestCommand;
import com.bapegg.stockpilot.approval.ManualQuantityTestExecutor;
import com.bapegg.stockpilot.approval.ManualQuantityTestResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RebalanceSimulationController {

    private final RebalanceSimulationService rebalanceSimulationService;
    private final ManualQuantityTestExecutor manualQuantityTestExecutor;

    public RebalanceSimulationController(
            RebalanceSimulationService rebalanceSimulationService,
            ManualQuantityTestExecutor manualQuantityTestExecutor) {
        this.rebalanceSimulationService = rebalanceSimulationService;
        this.manualQuantityTestExecutor = manualQuantityTestExecutor;
    }

    /**
     * Branches on the additive MVP-2 version tuple, per current-task.md section 2: absent
     * entirely keeps the exact legacy MVP-1 request/response shape and calculation; present it
     * must be all four fields, routed to the side-effect-free {@code MANUAL} quantity-test
     * executor. A partial tuple is rejected outright -- missing fields are never filled in from
     * the recommendation itself.
     */
    @PostMapping("/api/rebalancing-simulations")
    public Object simulate(@Valid @RequestBody RebalanceSimulationRequest request) {
        boolean anyVersionFieldPresent = request.analysisRunId() != null || request.inputSnapshotVersion() != null
                || request.ruleVersion() != null || request.candidateVersion() != null;
        if (!anyVersionFieldPresent) {
            return rebalanceSimulationService.simulate(request.recommendationId(), request.requestedQuantity());
        }

        boolean allVersionFieldsPresent = request.analysisRunId() != null && request.inputSnapshotVersion() != null
                && request.ruleVersion() != null && request.candidateVersion() != null;
        if (!allVersionFieldsPresent) {
            throw new ApprovalTransactionException(ApprovalErrorCode.INVALID_REQUEST,
                    "analysisRunId, inputSnapshotVersion, ruleVersion and candidateVersion must all be present "
                            + "or all be absent.");
        }

        ManualQuantityTestCommand command = new ManualQuantityTestCommand(
                request.recommendationId(), request.analysisRunId(), request.inputSnapshotVersion(),
                request.ruleVersion(), request.candidateVersion(), request.requestedQuantity());
        ManualQuantityTestResult result = manualQuantityTestExecutor.test(command);
        return Mvp2RebalanceSimulationResponse.from(result);
    }
}
