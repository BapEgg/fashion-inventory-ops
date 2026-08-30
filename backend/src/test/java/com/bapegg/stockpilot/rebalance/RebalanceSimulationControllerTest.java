package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import com.bapegg.stockpilot.approval.ApprovalTransactionException;
import com.bapegg.stockpilot.approval.ManualQuantityTestCommand;
import com.bapegg.stockpilot.approval.ManualQuantityTestExecutor;
import com.bapegg.stockpilot.approval.ManualQuantityTestResult;
import com.bapegg.stockpilot.demand.ManualQuantityProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RebalanceSimulationController}'s version-tuple routing, per
 * current-task.md section 7.1: absent tuple keeps the legacy MVP-1 path untouched, a complete
 * tuple routes to the MVP-2 {@code MANUAL} executor, and a partial tuple is rejected before
 * either is called. No Spring context, no Oracle -- both dependencies are mocked.
 */
class RebalanceSimulationControllerTest {

    private final RebalanceSimulationService legacyService = mock(RebalanceSimulationService.class);
    private final ManualQuantityTestExecutor manualExecutor = mock(ManualQuantityTestExecutor.class);
    private final RebalanceSimulationController controller =
            new RebalanceSimulationController(legacyService, manualExecutor);

    @Test
    void anAbsentVersionTupleCallsOnlyTheLegacyService() {
        RebalanceSimulationResponse legacyResponse = new RebalanceSimulationResponse(
                1L, 8, new StoreCoverage("S1", "Store 1", 5, null),
                new StoreCoverage("S1", "Store 1", 13, null),
                new StoreCoverage("S2", "Store 2", 10, null),
                new StoreCoverage("S2", "Store 2", 2, null));
        when(legacyService.simulate(1L, 8)).thenReturn(legacyResponse);

        Object result = controller.simulate(new RebalanceSimulationRequest(1L, 8, null, null, null, null));

        assertEquals(legacyResponse, result);
        verifyNoInteractions(manualExecutor);
    }

    @Test
    void aCompleteVersionTupleCallsOnlyTheManualExecutor() {
        ManualQuantityProjection projection = new ManualQuantityProjection(
                5, 13, null, null, null,
                10, 2, null, null, null,
                4, java.time.LocalDate.of(2026, 12, 5),
                0, 0, 0, 0, 0, 0);
        ManualQuantityTestResult result = new ManualQuantityTestResult(
                1L, 20L, "MVP-2-V1", "MVP-2", 1, 8, true, false, 8, 30, 8,
                List.of(), List.of(), 1, 1, 50, 30, 80,
                projection, true);
        when(manualExecutor.test(any())).thenReturn(result);

        Object response = controller.simulate(
                new RebalanceSimulationRequest(1L, 8, 20L, "MVP-2-V1", "MVP-2", 1));

        assertInstanceOf(Mvp2RebalanceSimulationResponse.class, response);
        Mvp2RebalanceSimulationResponse mvp2Response = (Mvp2RebalanceSimulationResponse) response;
        assertEquals(8, mvp2Response.recommendedBaseQuantity());
        assertEquals("ASSUMPTION", mvp2Response.assumption().type());
        verify(manualExecutor).test(new ManualQuantityTestCommand(1L, 20L, "MVP-2-V1", "MVP-2", 1, 8));
        verifyNoInteractions(legacyService);
    }

    @Test
    void aPartialVersionTupleIsRejectedWithoutCallingEitherPath() {
        RebalanceSimulationRequest request = new RebalanceSimulationRequest(1L, 8, 20L, null, null, null);

        ApprovalTransactionException e = assertThrows(ApprovalTransactionException.class,
                () -> controller.simulate(request));

        assertEquals(ApprovalErrorCode.INVALID_REQUEST, e.code());
        verifyNoInteractions(legacyService);
        verifyNoInteractions(manualExecutor);
    }

    @Test
    void aSingleMissingVersionFieldIsStillRejectedAsPartial() {
        // analysisRunId/inputSnapshotVersion/ruleVersion present, only candidateVersion missing.
        RebalanceSimulationRequest request = new RebalanceSimulationRequest(1L, 8, 20L, "MVP-2-V1", "MVP-2", null);

        assertThrows(ApprovalTransactionException.class, () -> controller.simulate(request));
        verifyNoInteractions(legacyService);
        verifyNoInteractions(manualExecutor);
    }
}
