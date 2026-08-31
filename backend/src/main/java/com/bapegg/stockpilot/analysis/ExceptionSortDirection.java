package com.bapegg.stockpilot.analysis;

/**
 * The MVP-2 run-bound exception list's explicit sort direction override, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} section 4.2. Named
 * distinctly from Spring Data's own {@code Sort.Direction} since this is a public API enum with
 * its own per-{@link ExceptionSortKey} default, not a generic paging concept.
 */
public enum ExceptionSortDirection {
    ASC,
    DESC
}
