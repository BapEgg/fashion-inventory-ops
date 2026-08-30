package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpDemandEventRepository extends JpaRepository<SpDemandEvent, Long> {

    /** Batch's single bulk read for this input version -- callers group the result in memory. */
    List<SpDemandEvent> findByInputSnapshotVersion(String inputSnapshotVersion);

    /** Approval/`MANUAL`'s single-lane re-read, per the shared current-basis contract. */
    List<SpDemandEvent> findByStoreIdAndSkuIdAndInputSnapshotVersion(String storeId, String skuId, String inputSnapshotVersion);
}
