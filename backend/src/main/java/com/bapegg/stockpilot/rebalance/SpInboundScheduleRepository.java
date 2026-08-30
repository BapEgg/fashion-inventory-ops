package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface SpInboundScheduleRepository extends JpaRepository<SpInboundSchedule, Long> {

    List<SpInboundSchedule> findByStoreIdAndSkuIdAndInputSnapshotVersionAndInboundStatus(
            String storeId, String skuId, String inputSnapshotVersion, InboundStatus inboundStatus);

    /**
     * The MVP-2 detail read API's full {@code inboundSchedules[]} evidence for one store-SKU
     * (any status), per current-task.md section 4.5 -- distinct from the single-status method
     * above, which only ever selects {@code CONFIRMED} rows for the approval transaction.
     */
    List<SpInboundSchedule> findByStoreIdAndSkuIdAndInputSnapshotVersion(
            String storeId, String skuId, String inputSnapshotVersion);

    /**
     * The MVP-2 list read API's {@code upcomingConfirmedInboundQuantity}/
     * {@code nextConfirmedInboundAt} evidence summary across an entire page of store-SKU pairs
     * at once, per current-task.md section 3 -- {@code storeIds}/{@code skuIds} are the page's
     * distinct id sets, not exact pairs; the caller matches each row's own
     * {@code (storeId, skuId)} back to the metric it belongs to.
     */
    @Query("""
            SELECT i FROM SpInboundSchedule i
            WHERE i.inputSnapshotVersion = :inputSnapshotVersion
                AND i.inboundStatus = com.bapegg.stockpilot.rebalance.InboundStatus.CONFIRMED
                AND i.storeId IN :storeIds AND i.skuId IN :skuIds
                AND i.quantity IS NOT NULL AND i.etaAt IS NOT NULL AND i.etaAt >= :cutoff
            """)
    List<SpInboundSchedule> findConfirmedForListSummary(
            @Param("inputSnapshotVersion") String inputSnapshotVersion,
            @Param("storeIds") Collection<String> storeIds,
            @Param("skuIds") Collection<String> skuIds,
            @Param("cutoff") OffsetDateTime cutoff);
}
