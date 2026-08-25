package com.bapegg.stockpilot.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SpDailySaleRepository extends JpaRepository<SpDailySale, Long> {

    List<SpDailySale> findByStoreIdAndSkuIdAndSalesDateBetween(
            String storeId, String skuId, LocalDate startInclusive, LocalDate endInclusive);
}
