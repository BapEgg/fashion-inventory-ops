package com.bapegg.stockpilot.demand;

/**
 * One {@code SP_STORE_TRANSFER_ROUTE} row (a directed donor-to-receiver store pair), per
 * {@code knowledge/business-rules.md} section 7. Immutable and independent of Spring/JPA.
 * Validates the same shape as V6's {@code ck_sp_route_values} Check Constraint.
 */
public record TransferRoute(
        boolean active,
        boolean ownerOverride,
        int leadTimeDays,
        int minimumQuantity,
        int packageMultiple,
        int maximumQuantity
) {

    public TransferRoute {
        if (leadTimeDays < 0) {
            throw new IllegalArgumentException("leadTimeDays must not be negative.");
        }
        if (minimumQuantity <= 0) {
            throw new IllegalArgumentException("minimumQuantity must be positive.");
        }
        if (packageMultiple <= 0) {
            throw new IllegalArgumentException("packageMultiple must be positive.");
        }
        if (maximumQuantity < minimumQuantity) {
            throw new IllegalArgumentException("maximumQuantity must be at least minimumQuantity.");
        }
    }
}
