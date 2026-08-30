package com.bapegg.stockpilot.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpMetricQualityFlagRepository extends JpaRepository<SpMetricQualityFlag, Long> {

    /**
     * Bulk quality-flag lookup for a page/detail's metric id set, per current-task.md sections
     * 3 and 4.2 -- one statement regardless of how many metrics or flags-per-metric exist.
     */
    List<SpMetricQualityFlag> findByInventoryMetric_InventoryMetricIdIn(Collection<Long> inventoryMetricIds);
}
