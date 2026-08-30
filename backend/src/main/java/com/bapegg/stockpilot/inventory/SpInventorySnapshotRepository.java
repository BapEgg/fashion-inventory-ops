package com.bapegg.stockpilot.inventory;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpInventorySnapshotRepository extends JpaRepository<SpInventorySnapshot, Long> {

    List<SpInventorySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    /**
     * The Phase 3 atomic output writer's one bulk read to build a store-SKU lookup map for the
     * claimed run's analysis date and input version, per current-task.md section 4 step 3.
     */
    List<SpInventorySnapshot> findBySnapshotDateAndInputSnapshotVersionOrderByStoreIdAscSkuIdAsc(
            LocalDate snapshotDate, String inputSnapshotVersion);

    /**
     * The MVP-2 detail read API's 28-day observation window evidence for one store-SKU, per
     * current-task.md section 4.4 -- version-scoped for the same reason as the sales-side
     * equivalent in {@code SpDailySaleRepository}.
     */
    List<SpInventorySnapshot> findByStoreIdAndSkuIdAndSnapshotDateBetweenAndInputSnapshotVersion(
            String storeId, String skuId, LocalDate startInclusive, LocalDate endInclusive, String inputSnapshotVersion);

    /**
     * The approval transaction's second lock (donor only, {@code APPROVED} only), taken
     * strictly after the recommendation lock -- business-rules.md section 10's fixed
     * lock order. Same 3-second demo {@code ASSUMPTION} timeout as the recommendation lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT s FROM SpInventorySnapshot s WHERE s.inventorySnapshotId = :inventorySnapshotId")
    Optional<SpInventorySnapshot> lockById(@Param("inventorySnapshotId") Long inventorySnapshotId);
}
