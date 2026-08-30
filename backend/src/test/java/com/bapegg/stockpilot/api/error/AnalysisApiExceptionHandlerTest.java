package com.bapegg.stockpilot.api.error;

import com.bapegg.stockpilot.analysis.AnalysisLaunchFailureClassifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AnalysisApiExceptionHandler}, per the Codex review findings:
 * the response's {@code type}/{@code code} must reflect the *effective* (fallback) presentation,
 * never the originally-requested code; {@code fieldErrors} entries must use the {@code code} key
 * (not {@code category}) sorted by {@code (field, code)}; a {@link DataAccessException} raised
 * outside a Job launch (e.g. a GET/pre-launch domain read) must still resolve through this
 * boundary rather than escaping it; and {@code fieldErrors} must never ride along on a response
 * whose *effective* code is not {@code VALIDATION_ERROR}. No Spring context.
 */
class AnalysisApiExceptionHandlerTest {

    private final ErrorCatalogService errorCatalogService = mock(ErrorCatalogService.class);
    private final AnalysisLaunchFailureClassifier failureClassifier = mock(AnalysisLaunchFailureClassifier.class);
    private final AnalysisApiExceptionHandler handler = new AnalysisApiExceptionHandler(errorCatalogService, failureClassifier);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void aFallbackPresentationsEffectiveCodeIsUsedInsteadOfTheOriginallyRequestedCode() {
        when(request.getRequestURI()).thenReturn("/api/analyses/1");
        // Simulates a catalog DB outage while resolving a perfectly valid code: the resolver
        // returns the fixed persistence-unavailable fallback, whose own code differs from what
        // was asked for.
        when(errorCatalogService.resolve(ApiErrorCode.ANALYSIS_NOT_FOUND))
                .thenReturn(ErrorPresentation.persistenceUnavailableFallback());

        ResponseEntity<ProblemDetail> response =
                handler.handleApiException(new ApiException(ApiErrorCode.ANALYSIS_NOT_FOUND, "not found"), request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, response.getBody().getProperties().get("code"),
                "The body code must be the fallback's own code, not the originally-requested ANALYSIS_NOT_FOUND.");
        assertEquals("urn:stockpilot:error:" + ApiErrorCode.PERSISTENCE_UNAVAILABLE, response.getBody().getType().toString());
    }

    @Test
    void anApiExceptionsFieldErrorsAreExposedWithTheCodeKeySortedByFieldThenCode() {
        when(request.getRequestURI()).thenReturn("/api/analyses");
        when(errorCatalogService.resolve(ApiErrorCode.VALIDATION_ERROR))
                .thenReturn(new ErrorPresentation(ApiErrorCode.VALIDATION_ERROR, 400, "요청 값 검증 실패", "상세", false));
        ApiException e = new ApiException(ApiErrorCode.VALIDATION_ERROR, "invalid", List.of(
                new ApiFieldError("zField", "SIZE", "m1"),
                new ApiFieldError("aField", "REQUIRED", "m2"),
                new ApiFieldError("aField", "FORMAT", "m3")));

        ResponseEntity<ProblemDetail> response = handler.handleApiException(e, request);

        @SuppressWarnings("unchecked")
        List<ApiFieldError> fieldErrors = (List<ApiFieldError>) response.getBody().getProperties().get("fieldErrors");
        assertEquals(3, fieldErrors.size());
        assertEquals(List.of("aField", "aField", "zField"), fieldErrors.stream().map(ApiFieldError::field).toList());
        assertEquals(List.of("FORMAT", "REQUIRED", "SIZE"), fieldErrors.stream().map(ApiFieldError::code).toList(),
                "Within the same field, entries must also sort by code.");
    }

    /** P1: a repository-level DataAccessException must resolve through this same boundary, not escape it. */
    @Test
    void aDataAccessExceptionIsClassifiedAndRespondedToThroughThisBoundary() {
        when(request.getRequestURI()).thenReturn("/api/analyses/1");
        DataAccessResourceFailureException raw = new DataAccessResourceFailureException("connection lost");
        ApiException classified = new ApiException(ApiErrorCode.PERSISTENCE_UNAVAILABLE, "unavailable");
        when(failureClassifier.classifyDataAccess(raw)).thenReturn(classified);
        when(errorCatalogService.resolve(ApiErrorCode.PERSISTENCE_UNAVAILABLE))
                .thenReturn(new ErrorPresentation(ApiErrorCode.PERSISTENCE_UNAVAILABLE, 503, "저장소 일시 불가", "상세", true));

        ResponseEntity<ProblemDetail> response = handler.handleDataAccessException(raw, request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, response.getBody().getProperties().get("code"));
        assertNull(response.getBody().getProperties().get("fieldErrors"));
    }

    /**
     * P2: an {@code ApiException} that carried validation fieldErrors must not expose them once
     * catalog resolution changes the *effective* presentation away from VALIDATION_ERROR (here,
     * the catalog lookup itself failed and fell back to persistence-unavailable).
     */
    @Test
    void fieldErrorsAreSuppressedWhenTheEffectivePresentationIsNotValidationError() {
        when(request.getRequestURI()).thenReturn("/api/analyses");
        when(errorCatalogService.resolve(ApiErrorCode.VALIDATION_ERROR))
                .thenReturn(ErrorPresentation.persistenceUnavailableFallback());
        ApiException e = new ApiException(ApiErrorCode.VALIDATION_ERROR, "invalid",
                List.of(new ApiFieldError("inputSnapshotVersion", "REQUIRED", "m")));

        ResponseEntity<ProblemDetail> response = handler.handleApiException(e, request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, response.getBody().getProperties().get("code"));
        assertNull(response.getBody().getProperties().get("fieldErrors"),
                "fieldErrors must never appear on a response whose effective code is not VALIDATION_ERROR.");
    }
}
