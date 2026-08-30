package com.bapegg.stockpilot.batch;

/** Grouping key for the already-approved active draft quantity sum, per donor store and SKU. */
public record Mvp2DonorSkuKey(String donorStoreId, String skuId) {
}
