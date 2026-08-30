package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import com.bapegg.stockpilot.approval.ApprovalTransactionCommand;
import com.bapegg.stockpilot.approval.ApprovalTransactionException;
import com.bapegg.stockpilot.approval.ApprovalTransactionFacade;
import com.bapegg.stockpilot.approval.ApprovalTransactionResult;
import com.bapegg.stockpilot.approval.PersistenceErrorTranslator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RebalanceDecisionController}'s MVP-2 signal routing, per
 * current-task.md section 7.1: no MVP-2 signal at all keeps the legacy MVP-1 path untouched, a
 * complete tuple plus exactly one {@code Idempotency-Key} header routes to the facade, and any
 * partial/malformed combination is rejected before either path runs. No Spring context, no
 * Oracle -- every dependency is mocked. Also covers the Codex review's P2 findings: the legacy
 * branch's field-shape pre-check must reject before ever calling the (mocked, DB-touching)
 * service, and a save-time {@link DataIntegrityViolationException} must be translated via
 * {@link PersistenceErrorTranslator}, not left to escape raw.
 */
class RebalanceDecisionControllerTest {

    private final RebalanceDecisionService legacyService = mock(RebalanceDecisionService.class);
    private final ApprovalTransactionFacade facade = mock(ApprovalTransactionFacade.class);
    private final Mvp2DecisionHistoryQueryService historyQueryService = mock(Mvp2DecisionHistoryQueryService.class);
    private final PersistenceErrorTranslator errorTranslator = mock(PersistenceErrorTranslator.class);
    private final RebalanceDecisionController controller =
            new RebalanceDecisionController(legacyService, facade, historyQueryService, errorTranslator);

    private static RebalanceDecisionRequest legacyRequest() {
        return new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "ok", "actor", null, null, null, null, null, null);
    }

    private static RebalanceDecisionRequest mvp2Request(Boolean policyException, String reasonCode) {
        return new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "ok", "actor",
                20L, "MVP-2-V1", "MVP-2", 1, policyException, reasonCode);
    }

    @Test
    void noMvp2SignalCallsOnlyTheLegacyServiceAndReturns201WithNoLocation() {
        RebalanceDecisionResponse legacyResponse = new RebalanceDecisionResponse(
                301L, 1L, DecisionStatus.APPROVED, 8, "ok", "actor", OffsetDateTime.now());
        when(legacyService.decide(1L, DecisionStatus.APPROVED, 8, "ok", "actor")).thenReturn(legacyResponse);

        ResponseEntity<Object> response = controller.decide(legacyRequest(), null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(legacyResponse, response.getBody());
        assertNull(response.getHeaders().getLocation());
        verifyNoInteractions(facade);
        verifyNoInteractions(historyQueryService);
    }

    @Test
    void aCompleteTupleAndExactlyOneHeaderCallsOnlyTheFacadeAndReturns201WhenCreated() {
        ApprovalTransactionResult result = new ApprovalTransactionResult(301L, DecisionStatus.APPROVED, 1, 401L, true);
        when(facade.execute(any(), any())).thenReturn(result);

        ResponseEntity<Object> response = controller.decide(mvp2Request(false, "MANUAL_OVERRIDE"), List.of("KEY-1"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/rebalancing-decisions/1", response.getHeaders().getLocation().toString());
        assertInstanceOf(Mvp2RebalanceDecisionResponse.class, response.getBody());
        Mvp2RebalanceDecisionResponse body = (Mvp2RebalanceDecisionResponse) response.getBody();
        assertEquals(301L, body.decisionId());
        assertEquals(1L, body.recommendationId());
        assertEquals(401L, body.transferDraftId());
        assertEquals(true, body.created());
        verify(facade).execute(new ApprovalTransactionCommand(
                1L, 20L, "MVP-2-V1", "MVP-2", 1, DecisionStatus.APPROVED, 8, false, "MANUAL_OVERRIDE", "ok", "actor"),
                "KEY-1");
        verifyNoInteractions(legacyService);
    }

    @Test
    void aReplayResultReturns200InsteadOf201() {
        ApprovalTransactionResult replay = new ApprovalTransactionResult(301L, DecisionStatus.APPROVED, 1, 401L, false);
        when(facade.execute(any(), any())).thenReturn(replay);

        ResponseEntity<Object> response = controller.decide(mvp2Request(false, "MANUAL_OVERRIDE"), List.of("KEY-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("/api/rebalancing-decisions/1", response.getHeaders().getLocation().toString());
        Mvp2RebalanceDecisionResponse body = (Mvp2RebalanceDecisionResponse) response.getBody();
        assertEquals(false, body.created());
    }

    @Test
    void omittedPolicyExceptionDefaultsToFalseInTheCommand() {
        ApprovalTransactionResult result = new ApprovalTransactionResult(301L, DecisionStatus.APPROVED, 1, 401L, true);
        when(facade.execute(any(), any())).thenReturn(result);

        controller.decide(mvp2Request(null, "MANUAL_OVERRIDE"), List.of("KEY-1"));

        verify(facade).execute(new ApprovalTransactionCommand(
                1L, 20L, "MVP-2-V1", "MVP-2", 1, DecisionStatus.APPROVED, 8, false, "MANUAL_OVERRIDE", "ok", "actor"),
                "KEY-1");
    }

    @Test
    void aPartialTupleIsRejectedWithoutCallingEitherPath() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "ok", "actor", 20L, null, null, null, null, null);

        ApprovalTransactionException e = assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(request, List.of("KEY-1")));

        assertEquals(ApprovalErrorCode.INVALID_DECISION_REQUEST, e.code());
        verifyNoInteractions(legacyService);
        verifyNoInteractions(facade);
    }

    @Test
    void aCompleteTupleWithoutTheHeaderIsRejected() {
        RebalanceDecisionRequest request = mvp2Request(false, "MANUAL_OVERRIDE");

        ApprovalTransactionException e = assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(request, null));

        assertEquals(ApprovalErrorCode.INVALID_DECISION_REQUEST, e.code());
        verifyNoInteractions(legacyService);
        verifyNoInteractions(facade);
    }

    @Test
    void twoHeaderOccurrencesAreRejectedAsMultiple() {
        RebalanceDecisionRequest request = mvp2Request(false, "MANUAL_OVERRIDE");

        assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(request, List.of("KEY-1", "KEY-2")));
        verifyNoInteractions(facade);
    }

    @Test
    void aCommaSeparatedSingleHeaderValueIsRejected() {
        RebalanceDecisionRequest request = mvp2Request(false, "MANUAL_OVERRIDE");

        assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(request, List.of("KEY-1,KEY-2")));
        verifyNoInteractions(facade);
    }

    @Test
    void anExplicitPolicyExceptionAloneWithNoTupleOrHeaderIsRejectedNotTreatedAsLegacy() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "ok", "actor", null, null, null, null, false, null);

        ApprovalTransactionException e = assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(request, null));

        assertEquals(ApprovalErrorCode.INVALID_DECISION_REQUEST, e.code());
        verifyNoInteractions(legacyService);
        verifyNoInteractions(facade);
    }

    @Test
    void anExplicitReasonCodeAloneWithNoTupleOrHeaderIsRejectedNotTreatedAsLegacy() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "ok", "actor", null, null, null, null, null, "MANUAL_OVERRIDE");

        assertThrows(ApprovalTransactionException.class, () -> controller.decide(request, null));
        verifyNoInteractions(legacyService);
        verifyNoInteractions(facade);
    }

    @Test
    void anEmptyHeaderListIsTreatedTheSameAsNoHeader() {
        RebalanceDecisionResponse legacyResponse = new RebalanceDecisionResponse(
                301L, 1L, DecisionStatus.APPROVED, 8, "ok", "actor", OffsetDateTime.now());
        when(legacyService.decide(1L, DecisionStatus.APPROVED, 8, "ok", "actor")).thenReturn(legacyResponse);

        ResponseEntity<Object> response = controller.decide(legacyRequest(), List.of());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verifyNoInteractions(facade);
    }

    @Test
    void getDelegatesToTheHistoryQueryService() {
        Mvp2DecisionHistoryResponse history = new Mvp2DecisionHistoryResponse(1L, DecisionStatus.PENDING, List.of());
        when(historyQueryService.getHistory(1L)).thenReturn(history);

        Mvp2DecisionHistoryResponse response = controller.history(1L);

        assertEquals(history, response);
    }

    @Test
    void aMissingSelectedQuantityOnALegacyBodyIsRejectedBeforeCallingTheService() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, null, "ok", "actor", null, null, null, null, null, null);

        ApiException e = assertThrows(ApiException.class, () -> controller.decide(request, null));

        assertEquals("VALIDATION_ERROR", e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(fe -> "selectedQuantity".equals(fe.field())
                && "REQUIRED".equals(fe.code())));
        verifyNoInteractions(legacyService);
    }

    @Test
    void aNonPositiveSelectedQuantityOnALegacyBodyIsRejectedBeforeCallingTheService() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 0, "ok", "actor", null, null, null, null, null, null);

        ApiException e = assertThrows(ApiException.class, () -> controller.decide(request, null));

        assertEquals("VALIDATION_ERROR", e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(fe -> "selectedQuantity".equals(fe.field())
                && "FORMAT".equals(fe.code())));
        verifyNoInteractions(legacyService);
    }

    @Test
    void aBlankReasonOnALegacyBodyIsRejectedBeforeCallingTheService() {
        RebalanceDecisionRequest request = new RebalanceDecisionRequest(
                1L, DecisionStatus.APPROVED, 8, "   ", "actor", null, null, null, null, null, null);

        ApiException e = assertThrows(ApiException.class, () -> controller.decide(request, null));

        assertEquals("VALIDATION_ERROR", e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(fe -> "reason".equals(fe.field()) && "REQUIRED".equals(fe.code())));
        verifyNoInteractions(legacyService);
    }

    @Test
    void aValidLegacyBodyStillReachesTheServiceUnchanged() {
        RebalanceDecisionResponse legacyResponse = new RebalanceDecisionResponse(
                301L, 1L, DecisionStatus.APPROVED, 8, "ok", "actor", OffsetDateTime.now());
        when(legacyService.decide(1L, DecisionStatus.APPROVED, 8, "ok", "actor")).thenReturn(legacyResponse);

        ResponseEntity<Object> response = controller.decide(legacyRequest(), null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(legacyResponse, response.getBody());
    }

    @Test
    void aSaveTimeConstraintViolationOnTheLegacyPathIsTranslatedNotLeftRaw() {
        DataIntegrityViolationException constraintViolation = new DataIntegrityViolationException("UQ_SP_DEC_REC_SEQ");
        when(legacyService.decide(1L, DecisionStatus.APPROVED, 8, "ok", "actor")).thenThrow(constraintViolation);
        ApprovalTransactionException translated =
                new ApprovalTransactionException(ApprovalErrorCode.DECISION_CONFLICT, "conflict", constraintViolation);
        when(errorTranslator.translate(constraintViolation)).thenReturn(translated);

        ApprovalTransactionException e = assertThrows(ApprovalTransactionException.class,
                () -> controller.decide(legacyRequest(), null));

        assertEquals(ApprovalErrorCode.DECISION_CONFLICT, e.code());
    }
}
