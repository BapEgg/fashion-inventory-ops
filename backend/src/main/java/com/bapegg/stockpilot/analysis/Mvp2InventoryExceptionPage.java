package com.bapegg.stockpilot.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The MVP-2 run-bound inventory-exception list envelope, per current-task.md section 3.
 * {@code items} is always non-null (empty list, never {@code null}, when a page has no rows).
 */
public record Mvp2InventoryExceptionPage(
        Long analysisRunId,
        LocalDate analysisDate,
        String inputSnapshotVersion,
        String ruleVersion,
        OffsetDateTime completedAt,
        String assumptionType,
        String assumptionNotice,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        List<Mvp2InventoryExceptionListItem> items,
        AllocatorWorkSummary summary
) {
}
