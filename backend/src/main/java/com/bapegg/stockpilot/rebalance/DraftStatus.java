package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code draft_status} check on {@code sp_transfer_draft} ({@code V6}).
 * business-rules.md limits MVP-2's implementation scope to {@link #CREATED} and
 * {@link #READY}; the rest exist for the physical contract but nothing in this
 * codebase transitions a draft to them yet.
 */
public enum DraftStatus {
    CREATED,
    READY,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
