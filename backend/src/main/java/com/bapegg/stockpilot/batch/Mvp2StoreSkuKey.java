package com.bapegg.stockpilot.batch;

/** Grouping key for one store-SKU's events and inbound schedule rows. */
public record Mvp2StoreSkuKey(String storeId, String skuId) {
}
