package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.TransferRoute;

/** One {@code SP_STORE_TRANSFER_ROUTE} row for the requested input version, donor/receiver
 * identity plus the pure {@link TransferRoute} the demand package calculates with. Active and
 * inactive routes are both loaded -- filtering to active-only is an orchestration concern. */
public record Mvp2Route(
        Long routeId,
        String donorStoreId,
        String receiverStoreId,
        TransferRoute route
) {
}
