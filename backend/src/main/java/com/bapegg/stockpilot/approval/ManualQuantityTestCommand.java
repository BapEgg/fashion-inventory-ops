package com.bapegg.stockpilot.approval;

/**
 * One normalized, shape-validated `MANUAL` quantity-test command, per
 * {@code knowledge/business-rules.md} section 10's quantity-test contract. Unlike
 * {@link ApprovalTransactionCommand}, this carries no actor, reason, status, policy-exception
 * flag or idempotency key -- it is a side-effect-free preview, not a decision, so none of those
 * concepts apply. Every string field is NFC-normalized and {@code strip()}ped here (via
 * {@link IdempotencyFingerprint#normalize}), same as the approval command. Shape violations
 * throw {@link ApprovalTransactionException} with {@link ApprovalErrorCode#INVALID_REQUEST}.
 */
public record ManualQuantityTestCommand(
        Long recommendationId,
        Long analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        int candidateVersion,
        int requestedQuantity
) {

    public ManualQuantityTestCommand {
        requirePositive(recommendationId, "recommendationId");
        requirePositive(analysisRunId, "analysisRunId");
        if (candidateVersion <= 0) {
            throw invalid("candidateVersion must be positive.");
        }
        if (requestedQuantity <= 0) {
            throw invalid("requestedQuantity must be positive.");
        }
        inputSnapshotVersion = requireNormalized(inputSnapshotVersion, "inputSnapshotVersion", 64);
        ruleVersion = requireNormalized(ruleVersion, "ruleVersion", 32);
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

    private static ApprovalTransactionException invalid(String message) {
        return new ApprovalTransactionException(ApprovalErrorCode.INVALID_REQUEST, message);
    }
}
