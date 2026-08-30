package com.bapegg.stockpilot.batch;

/** Grouping key for one donor-receiver store pair's transfer route rows (routes are not SKU-scoped). */
public record Mvp2StorePairKey(String donorStoreId, String receiverStoreId) {
}
