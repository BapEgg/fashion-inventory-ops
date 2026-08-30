package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.rebalance.SpErrorConstraintMapRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates a raw {@link DataAccessException} into a stable {@link ApprovalTransactionException},
 * per {@code knowledge/business-rules.md} section 10's error-translation contract. Never
 * exposes the root SQL/constraint/stack message in the resulting exception's own
 * message text -- callers that need it for logging can still read {@link Throwable#getCause()}.
 */
@Component
public class PersistenceErrorTranslator {

    /** Matches Oracle's "constraint (SCHEMA.CONSTRAINT_NAME) violated" message shape. */
    private static final Pattern ORACLE_CONSTRAINT_PATTERN = Pattern.compile("\\(([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\)");

    private final SpErrorConstraintMapRepository constraintMapRepository;

    public PersistenceErrorTranslator(SpErrorConstraintMapRepository constraintMapRepository) {
        this.constraintMapRepository = constraintMapRepository;
    }

    public ApprovalTransactionException translate(DataAccessException e) {
        if (isLockTimeout(e)) {
            return new ApprovalTransactionException(
                    ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT, "Timed out waiting for a lock.", e);
        }
        if (e instanceof DataIntegrityViolationException) {
            Optional<String> mappedCode = constraintName(e).flatMap(this::lookupErrorCode);
            if (mappedCode.isPresent()) {
                return new ApprovalTransactionException(mappedCode.get(), "A database constraint was violated.", e);
            }
        }
        if (isConnectionFailure(e)) {
            return new ApprovalTransactionException(
                    ApprovalErrorCode.PERSISTENCE_UNAVAILABLE, "Persistence is temporarily unavailable.", e);
        }
        return new ApprovalTransactionException(
                ApprovalErrorCode.INTERNAL_SERVER_ERROR, "An unexpected persistence failure occurred.", e);
    }

    /** Extracts just the constraint name (e.g. {@code UQ_SP_DEC_REQUEST_ID}) from the root cause message, if present. */
    public Optional<String> constraintName(DataAccessException e) {
        String message = rootMessage(e);
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = ORACLE_CONSTRAINT_PATTERN.matcher(message);
        return matcher.find() ? Optional.of(matcher.group(2)) : Optional.empty();
    }

    private Optional<String> lookupErrorCode(String constraintName) {
        return constraintMapRepository.findById(constraintName.toUpperCase(Locale.ROOT))
                .map(com.bapegg.stockpilot.rebalance.SpErrorConstraintMap::getErrorCode);
    }

    private boolean isLockTimeout(DataAccessException e) {
        if (e instanceof PessimisticLockingFailureException
                || e instanceof CannotAcquireLockException
                || e instanceof QueryTimeoutException) {
            return true;
        }
        String message = rootMessage(e);
        return message != null && (message.contains("ORA-00054") || message.contains("ORA-30006"));
    }

    private boolean isConnectionFailure(DataAccessException e) {
        return e instanceof DataAccessResourceFailureException || e instanceof TransientDataAccessResourceException;
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
