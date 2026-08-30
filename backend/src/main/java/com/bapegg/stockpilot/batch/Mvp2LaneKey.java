package com.bapegg.stockpilot.batch;

/** Grouping key for one donor-receiver-SKU lane's open transfer rows. */
public record Mvp2LaneKey(String donorStoreId, String receiverStoreId, String skuId) {
}
