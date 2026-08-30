package com.bapegg.stockpilot.batch;

/**
 * Signals that the requested {@code (analysisDate, inputSnapshotVersion)} does not satisfy
 * {@code data-model.md}'s Phase 3 input contract (missing anchor, incomplete 28-day history,
 * a quantity that will not fit the domain's integer range, or an internally inconsistent raw
 * row). The Batch orchestration this adapter feeds must persist no output and mark the run
 * {@code FAILED} when this is thrown -- never partially apply a broken input.
 */
public class InputContractViolationException extends RuntimeException {

    public InputContractViolationException(String message) {
        super(message);
    }

    public InputContractViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
