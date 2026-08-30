package com.bapegg.stockpilot.api.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ErrorCatalogService}'s fallback rules, per current-task.md section 3's
 * closing paragraph. No Spring context, no Oracle.
 */
class ErrorCatalogServiceTest {

    private final SpErrorCatalogRepository repository = mock(SpErrorCatalogRepository.class);
    private final ErrorCatalogService service = new ErrorCatalogService(repository);

    @Test
    void resolvesAnActiveRowStraightFromTheCatalog() {
        SpErrorCatalog row = catalogRow("ANALYSIS_ALREADY_RUNNING", 409, true, "분석 실행 중", "메시지", "상세 내용");
        when(repository.findById("ANALYSIS_ALREADY_RUNNING")).thenReturn(Optional.of(row));

        ErrorPresentation presentation = service.resolve("ANALYSIS_ALREADY_RUNNING");

        assertEquals("ANALYSIS_ALREADY_RUNNING", presentation.code());
        assertEquals(409, presentation.httpStatus());
        assertEquals("분석 실행 중", presentation.title());
        assertEquals("상세 내용", presentation.detail());
        assertTrue(presentation.retryable());
    }

    /**
     * Per the P1 finding: the fallback's *effective* code must be {@code INTERNAL_SERVER_ERROR},
     * not the originally-requested code -- otherwise a 500 response could carry an unrelated code
     * (e.g. a 404-shaped one), a status/code contradiction a client cannot safely branch on.
     */
    @Test
    void fallsBackToInternalWithTheInternalCodeWhenTheRowIsInactive() {
        SpErrorCatalog row = catalogRow("SOME_CODE", 409, false, "제목", "메시지", "상세");
        when(repository.findById("SOME_CODE")).thenReturn(Optional.of(row));

        ErrorPresentation presentation = service.resolve("SOME_CODE");

        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, presentation.code());
        assertEquals(500, presentation.httpStatus());
        assertFalse(presentation.retryable());
    }

    @Test
    void fallsBackToInternalWithTheInternalCodeWhenTheRowIsMissing() {
        when(repository.findById("UNKNOWN_CODE")).thenReturn(Optional.empty());

        ErrorPresentation presentation = service.resolve("UNKNOWN_CODE");

        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, presentation.code());
        assertEquals(500, presentation.httpStatus());
        assertFalse(presentation.retryable());
    }

    @Test
    void fallsBackToPersistenceUnavailableWithThatCodeWhenTheLookupItselfFails() {
        when(repository.findById("ANY_CODE")).thenThrow(new DataAccessResourceFailureException("DB down"));

        ErrorPresentation presentation = service.resolve("ANY_CODE");

        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, presentation.code());
        assertEquals(503, presentation.httpStatus());
        assertTrue(presentation.retryable());
    }

    /** {@code SpErrorCatalog} has no public constructor (read-only JPA entity) -- built via reflection for this test. */
    private static SpErrorCatalog catalogRow(
            String errorCode, int httpStatus, boolean active, String title, String message, String detail) {
        try {
            SpErrorCatalog row = instantiate();
            setField(row, "errorCode", errorCode);
            setField(row, "httpStatus", httpStatus);
            setField(row, "retryableFlag", active ? "Y" : "N");
            setField(row, "activeFlag", active ? "Y" : "N");
            setField(row, "titleKo", title);
            setField(row, "messageKo", message);
            setField(row, "defaultDetailKo", detail);
            return row;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static SpErrorCatalog instantiate() throws ReflectiveOperationException {
        var constructor = SpErrorCatalog.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void setField(SpErrorCatalog row, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = SpErrorCatalog.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(row, value);
    }
}
