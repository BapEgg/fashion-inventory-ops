package com.bapegg.stockpilot.demand;

/**
 * Every hard-constraint a manually requested transfer quantity can violate, per
 * {@code knowledge/business-rules.md} section 10's `MANUAL` quantity-test contract. Declaration
 * order is fixed and is the order {@link ManualQuantityEvaluation#calculate} checks and reports
 * them in -- a request can violate more than one at once, and every applicable one is returned,
 * not just the first.
 */
public enum ManualQuantityViolation {
    CANDIDATE_INELIGIBLE,
    BELOW_ROUTE_MINIMUM,
    NOT_PACKAGE_MULTIPLE,
    EXCEEDS_DONOR_TRANSFERABLE,
    EXCEEDS_ROUTE_MAXIMUM,
    EXCEEDS_RECEIVER_CAPACITY
}
