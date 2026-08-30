package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.rebalance.OpenTransferStatus;

/** One raw {@code SP_OPEN_TRANSFER} row for the requested input version. */
public record Mvp2OpenTransferRow(
        String donorStoreId,
        String receiverStoreId,
        String skuId,
        int quantity,
        OpenTransferStatus transferStatus
) {
}
