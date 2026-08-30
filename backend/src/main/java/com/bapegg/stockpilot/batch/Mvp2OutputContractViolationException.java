package com.bapegg.stockpilot.batch;

/**
 * Thrown by {@link Mvp2AtomicOutputWriter} when a {@link Mvp2CalculationResult} fails the
 * structural checks in current-task.md section 4 step 2/3/6 (a run-identity mismatch, a
 * duplicate metric key, a flat/index mismatch, a missing snapshot, or an unresolved candidate
 * metric FK). This is always a defect in the caller or the input evidence, never a candidate
 * eligibility/reason/scenario judgment -- the writer never re-decides those.
 */
public class Mvp2OutputContractViolationException extends RuntimeException {

    public Mvp2OutputContractViolationException(String message) {
        super(message);
    }
}
