package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code inbound_status} check on {@code sp_inbound_schedule} ({@code V6}).
 */
public enum InboundStatus {
    PLANNED,
    CONFIRMED,
    CANCELLED,
    RECEIVED
}
