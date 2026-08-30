package com.bapegg.stockpilot.demand;

/**
 * A quality flag attached to one metric's calculation, per {@code knowledge/business-rules.md}
 * section 4. Multiple flags can co-occur on the same store-SKU; unlike {@link DemandSignalType}
 * (a single primary signal), all applicable flags are stored.
 */
public enum MetricQualityFlag {
    OOS_CENSORED,
    STALE_INVENTORY,
    MISSING_INBOUND,
    INCOMPLETE_EVENT_DATA
}
