package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpStoreSkuPolicyRepository extends JpaRepository<SpStoreSkuPolicy, Long> {

    Optional<SpStoreSkuPolicy> findByStoreIdAndSkuIdAndInputSnapshotVersion(
            String storeId, String skuId, String inputSnapshotVersion);
}
