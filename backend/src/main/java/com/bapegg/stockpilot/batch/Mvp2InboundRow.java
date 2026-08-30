package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.rebalance.InboundStatus;

import java.time.OffsetDateTime;

/**
 * One raw {@code SP_INBOUND_SCHEDULE} row for the requested input version. {@code quantity}/
 * {@code etaAt} are nullable so a caller can detect an incomplete reference (the
 * {@code MISSING_INBOUND} quality flag, per {@code knowledge/business-rules.md} section 4) --
 * this row is loaded as-is; the input adapter does not reject an incomplete inbound reference
 * itself, since incomplete rows are still valid input for that quality flag.
 */
public record Mvp2InboundRow(
        String storeId,
        String skuId,
        Integer quantity,
        OffsetDateTime etaAt,
        InboundStatus inboundStatus
) {

    public boolean isComplete() {
        return quantity != null && etaAt != null;
    }
}
