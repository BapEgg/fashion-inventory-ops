package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpStoreTransferRouteRepository extends JpaRepository<SpStoreTransferRoute, Long> {

    /**
     * Every route (any donor) into one receiver for an input version, per
     * {@code knowledge/business-rules.md} section 10's shared current-basis contract: the plan
     * horizon used to decide event relevance is built from the receiver's full active-route lead
     * time list, not just the one recommendation route being evaluated.
     */
    List<SpStoreTransferRoute> findByReceiverStoreIdAndInputSnapshotVersion(
            String receiverStoreId, String inputSnapshotVersion);
}
