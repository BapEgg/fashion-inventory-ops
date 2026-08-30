package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.rebalance.SpErrorConstraintMap;
import com.bapegg.stockpilot.rebalance.SpErrorConstraintMapRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for {@link PersistenceErrorTranslator}'s classification rules,
 * per the Codex review finding that its constraint-map lookup must never run inside an
 * already-failed {@code @Transactional} session -- see
 * {@link ApprovalTransactionExecutor#execute} and {@link ApprovalTransactionFacade#execute}.
 * Uses a mocked {@link SpErrorConstraintMapRepository} rather than Oracle, since this
 * class's classification logic is deterministic pure Java plus one repository call.
 */
class PersistenceErrorTranslatorTest {

    private final SpErrorConstraintMapRepository constraintMapRepository = mock(SpErrorConstraintMapRepository.class);
    private final PersistenceErrorTranslator translator = new PersistenceErrorTranslator(constraintMapRepository);

    @Test
    void extractsTheSchemaQualifiedConstraintNameFromARealisticOracleMessage() {
        DataIntegrityViolationException e = oracleConstraintViolation("STOCKPILOT", "UQ_SP_DEC_REQUEST_ID");

        assertEquals(Optional.of("UQ_SP_DEC_REQUEST_ID"), translator.constraintName(e));
    }

    @Test
    void constraintNameIsAbsentWhenTheMessageHasNoParenthesizedConstraint() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("no constraint mentioned here");

        assertTrue(translator.constraintName(e).isEmpty());
    }

    @Test
    void aMappedConstraintTranslatesToItsCatalogErrorCode() {
        SpErrorConstraintMap mapping = mock(SpErrorConstraintMap.class);
        when(mapping.getErrorCode()).thenReturn("DECISION_CONFLICT");
        when(constraintMapRepository.findById("UQ_SP_DEC_REC_SEQ")).thenReturn(Optional.of(mapping));

        ApprovalTransactionException result =
                translator.translate(oracleConstraintViolation("STOCKPILOT", "UQ_SP_DEC_REC_SEQ"));

        assertEquals("DECISION_CONFLICT", result.code());
    }

    @Test
    void anUnmappedConstraintFallsBackToInternalServerError() {
        when(constraintMapRepository.findById("SOME_UNKNOWN_CONSTRAINT")).thenReturn(Optional.empty());

        ApprovalTransactionException result =
                translator.translate(oracleConstraintViolation("STOCKPILOT", "SOME_UNKNOWN_CONSTRAINT"));

        assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, result.code());
    }

    @Test
    void aConstraintViolationNeverConsultsTheMapWhenNoConstraintNameCanBeExtracted() {
        ApprovalTransactionException result = translator.translate(new DataIntegrityViolationException("opaque failure"));

        assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, result.code());
        verifyNoInteractions(constraintMapRepository);
    }

    @Test
    void classifiesKnownLockExceptionTypesAsLockTimeoutWithoutConsultingTheConstraintMap() {
        assertEquals(ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT,
                translator.translate(new PessimisticLockingFailureException("lock")).code());
        assertEquals(ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT,
                translator.translate(new CannotAcquireLockException("lock")).code());
        verifyNoInteractions(constraintMapRepository);
    }

    @Test
    void classifiesAnOracleLockTimeoutMessagePatternAsLockTimeoutBeforeConstraintClassification() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("insert failed",
                new RuntimeException("ORA-00054: resource busy and acquire with NOWAIT specified or timeout expired"));

        assertEquals(ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT, translator.translate(e).code());
        verifyNoInteractions(constraintMapRepository);
    }

    @Test
    void classifiesConnectionFailuresAsPersistenceUnavailable() {
        assertEquals(ApprovalErrorCode.PERSISTENCE_UNAVAILABLE,
                translator.translate(new DataAccessResourceFailureException("down")).code());
        assertEquals(ApprovalErrorCode.PERSISTENCE_UNAVAILABLE,
                translator.translate(new TransientDataAccessResourceException("down")).code());
    }

    @Test
    void neverExposesTheRootSqlOrConstraintNameInTheTranslatedExceptionMessage() {
        ApprovalTransactionException result = translator.translate(
                oracleConstraintViolation("STOCKPILOT", "UQ_SP_DEC_REQUEST_ID"));

        assertFalse(result.getMessage().contains("ORA-"));
        assertFalse(result.getMessage().contains("UQ_SP_DEC_REQUEST_ID"));
    }

    private static DataIntegrityViolationException oracleConstraintViolation(String schema, String constraintName) {
        String oracleMessage = "ORA-00001: unique constraint (" + schema + "." + constraintName + ") violated";
        return new DataIntegrityViolationException("insert failed", new RuntimeException(oracleMessage));
    }
}
