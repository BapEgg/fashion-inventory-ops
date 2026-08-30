package com.bapegg.stockpilot.demand;

/**
 * {@code scenario_type}, per {@code knowledge/business-rules.md} section 8 -- the four values
 * {@code sp_rebalance_scenario} persists. {@code MANUAL} quantity testing is a separate,
 * side-effect-free API calculation over a user-supplied transfer **quantity** (never a rate);
 * it is not one of these four and is not persisted, so it has no value here -- see
 * {@link ManualQuantityEvaluation}.
 */
public enum TransferScenarioType {
    NO_ACTION,
    CONSERVATIVE,
    BASE,
    AGGRESSIVE
}
