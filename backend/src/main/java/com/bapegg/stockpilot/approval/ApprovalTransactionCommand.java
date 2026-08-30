package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.rebalance.DecisionStatus;

/**
 * One normalized, shape-validated approval command, per
 * {@code knowledge/business-rules.md} section 10. This is the application-layer input
 * to {@link ApprovalTransactionFacade#execute} -- the idempotency key is a separate
 * facade parameter, not a command field, since it is not part of what the fingerprint
 * covers.
 * <p>
 * Every string field is NFC-normalized and {@code strip()}ped here (via
 * {@link IdempotencyFingerprint#normalize}) before any length check runs, so the
 * fingerprint later computed from these same fields is stable regardless of how the
 * caller originally formatted whitespace/composition. Shape violations throw
 * {@link ApprovalTransactionException} with {@link ApprovalErrorCode#INVALID_DECISION_REQUEST}
 * -- this is a caller-contract defect, not a staleness or conflict outcome.
 * <p>
 * Only {@link DecisionStatus#HELD}, {@link DecisionStatus#APPROVED} and
 * {@link DecisionStatus#REJECTED} are legal command statuses: widening
 * {@link DecisionStatus} itself to cover every MVP-2 persistence state (so the JPA
 * mapping can read {@code PENDING}/{@code EXPIRED} rows an append-only writer produces)
 * must not silently widen what this command accepts.
 */
public record ApprovalTransactionCommand(
        Long recommendationId,
        Long analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        int candidateVersion,
        DecisionStatus status,
        Integer selectedQuantity,
        boolean policyException,
        String reasonCode,
        String reason,
        String actorLabel
) {

    public ApprovalTransactionCommand {
        requirePositive(recommendationId, "recommendationId");
        requirePositive(analysisRunId, "analysisRunId");
        if (candidateVersion <= 0) {
            throw invalid("candidateVersion must be positive.");
        }
        inputSnapshotVersion = requireNormalized(inputSnapshotVersion, "inputSnapshotVersion", 64);
        ruleVersion = requireNormalized(ruleVersion, "ruleVersion", 32);
        actorLabel = requireNormalized(actorLabel, "actorLabel", 100);
        reasonCode = normalizeOptional(reasonCode, "reasonCode", 40);
        reason = normalizeOptional(reason, "reason", 1000);

        if (status != DecisionStatus.HELD && status != DecisionStatus.APPROVED && status != DecisionStatus.REJECTED) {
            throw invalid("status must be HELD, APPROVED, or REJECTED.");
        }
        if (policyException && status != DecisionStatus.APPROVED) {
            throw invalid("policyException is only legal when status is APPROVED.");
        }
        if (status == DecisionStatus.APPROVED) {
            if (selectedQuantity == null || selectedQuantity <= 0) {
                throw invalid("selectedQuantity must be positive when status is APPROVED.");
            }
        } else {
            if (selectedQuantity != null) {
                throw invalid("selectedQuantity must be null when status is HELD or REJECTED.");
            }
            if (reasonCode == null || reason == null) {
                throw invalid("reasonCode and reason are required when status is HELD or REJECTED.");
            }
        }
    }

    /** The idempotency fingerprint over this command's own (already-normalized) fields. */
    public String fingerprint() {
        return IdempotencyFingerprint.compute(
                recommendationId, analysisRunId, inputSnapshotVersion, ruleVersion, candidateVersion,
                status.name(), selectedQuantity, policyException, reasonCode, reason, actorLabel);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw invalid(name + " must be positive.");
        }
    }

    private static String requireNormalized(String value, String name, int maxLength) {
        String normalized = IdempotencyFingerprint.normalize(value);
        if (normalized == null) {
            throw invalid(name + " must not be blank.");
        }
        if (normalized.length() > maxLength) {
            throw invalid(name + " must be at most " + maxLength + " characters.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, String name, int maxLength) {
        String normalized = IdempotencyFingerprint.normalize(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw invalid(name + " must be at most " + maxLength + " characters.");
        }
        return normalized;
    }

    private static ApprovalTransactionException invalid(String message) {
        return new ApprovalTransactionException(ApprovalErrorCode.INVALID_DECISION_REQUEST, message);
    }
}
