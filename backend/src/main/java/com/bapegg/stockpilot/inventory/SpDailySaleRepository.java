package com.bapegg.stockpilot.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SpDailySaleRepository extends JpaRepository<SpDailySale, Long> {

    List<SpDailySale> findByStoreIdAndSkuIdAndSalesDateBetween(
            String storeId, String skuId, LocalDate startInclusive, LocalDate endInclusive);

    /**
     * The MVP-2 detail read API's 28-day observation window evidence for one store-SKU, per
     * current-task.md section 4.4 -- version-scoped so an {@code MVP-1-LEGACY} row sharing the
     * same date/store/sku never mixes into a versioned MVP-2 window.
     */
    List<SpDailySale> findByStoreIdAndSkuIdAndSalesDateBetweenAndInputSnapshotVersion(
            String storeId, String skuId, LocalDate startInclusive, LocalDate endInclusive, String inputSnapshotVersion);
}
