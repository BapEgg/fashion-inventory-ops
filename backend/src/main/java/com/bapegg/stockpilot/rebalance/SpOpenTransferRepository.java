package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SpOpenTransferRepository extends JpaRepository<SpOpenTransfer, Long> {

    List<SpOpenTransfer> findByReceiverStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
            String receiverStoreId, String skuId, String inputSnapshotVersion, Collection<OpenTransferStatus> statuses);

    List<SpOpenTransfer> findByDonorStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
            String donorStoreId, String skuId, String inputSnapshotVersion, Collection<OpenTransferStatus> statuses);

    boolean existsByDonorStoreIdAndReceiverStoreIdAndSkuIdAndInputSnapshotVersionAndTransferStatusIn(
            String donorStoreId, String receiverStoreId, String skuId, String inputSnapshotVersion,
            Collection<OpenTransferStatus> statuses);

    /**
     * The MVP-2 detail read API's single {@code openTransfers} source, per current-task.md
     * section 5's query ceiling -- merges the receiver-side and donor-side lookups above (both
     * still used, unchanged, by the approval transaction) into one statement. Callers determine
     * each row's direction themselves by comparing {@code storeId} against
     * {@link SpOpenTransfer#getReceiverStoreId()}/{@link SpOpenTransfer#getDonorStoreId()}.
     */
    @Query("""
            SELECT t FROM SpOpenTransfer t
            WHERE t.inputSnapshotVersion = :inputSnapshotVersion AND t.skuId = :skuId
                AND t.transferStatus IN :statuses
                AND (t.receiverStoreId = :storeId OR t.donorStoreId = :storeId)
            """)
    List<SpOpenTransfer> findOpenForStore(
            @Param("storeId") String storeId,
            @Param("skuId") String skuId,
            @Param("inputSnapshotVersion") String inputSnapshotVersion,
            @Param("statuses") Collection<OpenTransferStatus> statuses);
}
