package com.bapegg.stockpilot.rebalance;

/** {@code SP_DEMAND_EVENT.event_type} allowed values, per {@code V6}. Classification metadata
 * only -- it does not affect uplift calculation, which uses only the low/base/high factors. */
public enum DemandEventType {
    PROMOTION,
    PRICE_CHANGE,
    STORE_EVENT,
    OTHER
}
