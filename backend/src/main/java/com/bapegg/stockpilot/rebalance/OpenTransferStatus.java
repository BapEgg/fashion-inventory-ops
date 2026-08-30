package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code transfer_status} check on {@code sp_open_transfer} ({@code V6}).
 */
public enum OpenTransferStatus {
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    CANCELLED,
    RECEIVED
}
