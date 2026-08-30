package com.bapegg.stockpilot.approval;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The single entry point for the approval command, per
 * {@code knowledge/business-rules.md} section 10. Not itself {@code @Transactional} --
 * it orchestrates {@link ApprovalTransactionReader}'s read-only idempotency lookup and
 * {@link ApprovalTransactionExecutor}'s write transaction, each a separate Spring bean
 * so each keeps its own transaction boundary. Called by
 * {@code com.bapegg.stockpilot.rebalance.RebalanceDecisionController}'s MVP-2 branch; its
 * {@code ProblemDetail} wiring is {@code AnalysisApiExceptionHandler}'s
 * {@code ApprovalTransactionException} handler.
 */
@Service
public class ApprovalTransactionFacade {

    private final ApprovalTransactionReader reader;
    private final ApprovalTransactionExecutor executor;
    private final PersistenceErrorTranslator errorTranslator;

    public ApprovalTransactionFacade(
            ApprovalTransactionReader reader,
            ApprovalTransactionExecutor executor,
            PersistenceErrorTranslator errorTranslator) {
        this.reader = reader;
        this.executor = executor;
        this.errorTranslator = errorTranslator;
    }

    public ApprovalTransactionResult execute(ApprovalTransactionCommand command, String idempotencyKey) {
        String normalizedKey = IdempotencyFingerprint.normalize(idempotencyKey);
        if (normalizedKey == null || normalizedKey.length() > 100) {
            throw new ApprovalTransactionException(ApprovalErrorCode.INVALID_DECISION_REQUEST,
                    "idempotencyKey must be 1..100 characters after normalization.");
        }
        String fingerprint = command.fingerprint();

        Optional<ApprovalTransactionReader.ExistingKeyLookup> existing = reader.findExisting(normalizedKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), fingerprint);
        }

        try {
            return executor.execute(command, normalizedKey, fingerprint);
        } catch (DataIntegrityViolationException e) {
            if (isReusedIdempotencyKey(e)) {
                // Someone else won the race on a DIFFERENT recommendation (the only case this
                // constraint can still fire, since the executor already re-checks the key once
                // it holds the recommendation lock). That transaction is already rolled back;
                // re-read the winner as a fresh, separate read.
                ApprovalTransactionReader.ExistingKeyLookup winner = reader.findExisting(normalizedKey)
                        .orElseThrow(() -> new ApprovalTransactionException(ApprovalErrorCode.INTERNAL_SERVER_ERROR,
                                "Expected a winning decision for a reused idempotency key but found none.", e));
                return resolveExisting(winner, fingerprint);
            }
            throw errorTranslator.translate(e);
        }
    }

    private ApprovalTransactionResult resolveExisting(
            ApprovalTransactionReader.ExistingKeyLookup existing, String incomingFingerprint) {
        if (!existing.fingerprint().equals(incomingFingerprint)) {
            throw new ApprovalTransactionException(ApprovalErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "decisionRequestId already used for a different request payload.");
        }
        return existing.replayResult();
    }

    private boolean isReusedIdempotencyKey(DataIntegrityViolationException e) {
        return errorTranslator.constraintName(e)
                .map(name -> name.equalsIgnoreCase("UQ_SP_DEC_REQUEST_ID"))
                .orElse(false);
    }
}
