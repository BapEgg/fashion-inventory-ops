package com.bapegg.stockpilot.analysis;

/**
 * JPQL expression fragments shared by {@link SpInventoryMetricRepository}'s
 * {@code findPagedIds}/{@code countPaged} queries, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} sections 4.1-4.2.
 * <p>
 * Every fragment here is a compile-time constant so it can be concatenated directly into a
 * {@code @Query} annotation value (a real Java requirement, not a style choice), and the same
 * constants are reused wherever the identical condition must appear more than once in one query
 * (the {@code WORK_STATUS} filter and every {@code WORK_PRIORITY} sort sub-key), so the derivation
 * can never drift between occurrences inside the SQL layer itself. This SQL-level derivation is
 * intentionally kept in lockstep with -- but is a separate implementation from --
 * {@link AllocatorWorkStatusResolver}'s Java derivation used for the actual per-row
 * {@code workStatus} field; {@code Mvp2InventoryExceptionReadOracleIT} asserts the two never
 * disagree.
 */
final class InventoryExceptionQuerySql {

    private InventoryExceptionQuerySql() {
    }

    private static final String CANDIDATE_BASE =
            "r.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE "
            + "AND r.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED";

    /** Correlated EXISTS: {@code m} (aliased in the enclosing query) has any executable candidate. */
    static final String EXECUTABLE_CANDIDATE_EXISTS =
            "EXISTS (SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r "
            + "WHERE (r.receiverMetric = m OR r.donorMetric = m) AND " + CANDIDATE_BASE + ")";

    /** Correlated EXISTS: an executable candidate on {@code m} with no decision row (logical PENDING). */
    private static final String UNDECIDED_EXECUTABLE_CANDIDATE_EXISTS =
            "EXISTS (SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r "
            + "WHERE (r.receiverMetric = m OR r.donorMetric = m) AND " + CANDIDATE_BASE + " "
            + "AND NOT EXISTS (SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceDecision d "
            + "WHERE d.recommendation = r))";

    /** Correlated EXISTS: an executable candidate on {@code m} whose latest decision is HELD. */
    private static final String HELD_EXECUTABLE_CANDIDATE_EXISTS =
            "EXISTS (SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r "
            + "WHERE (r.receiverMetric = m OR r.donorMetric = m) AND " + CANDIDATE_BASE + " "
            + "AND EXISTS (SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceDecision d "
            + "WHERE d.recommendation = r AND d.decisionStatus = com.bapegg.stockpilot.rebalance.DecisionStatus.HELD "
            + "AND d.decisionSequence = (SELECT MAX(d2.decisionSequence) "
            + "FROM com.bapegg.stockpilot.rebalance.SpRebalanceDecision d2 WHERE d2.recommendation = r)))";

    private static final String REVIEW_OR_NON_ACTIONABLE =
            "m.inventoryExceptionType IN ("
            + "com.bapegg.stockpilot.demand.InventoryExceptionType.REVIEW_REQUIRED, "
            + "com.bapegg.stockpilot.demand.InventoryExceptionType.NON_ACTIONABLE)";

    /**
     * Returns the {@link AllocatorWorkStatus} enum name as a string, for the {@code workStatus}
     * repeatable filter. Precedence matches {@link AllocatorWorkStatusResolver#resolve}.
     */
    static final String WORK_STATUS_NAME_CASE =
            "CASE "
            + "WHEN " + UNDECIDED_EXECUTABLE_CANDIDATE_EXISTS + " THEN 'DECISION_REQUIRED' "
            + "WHEN " + HELD_EXECUTABLE_CANDIDATE_EXISTS + " THEN 'ON_HOLD' "
            + "WHEN " + EXECUTABLE_CANDIDATE_EXISTS + " THEN 'COMPLETED' "
            + "WHEN " + REVIEW_OR_NON_ACTIONABLE + " THEN 'REVIEW_INPUT' "
            + "ELSE 'NO_TRANSFER_OPTION' END";

    /**
     * The same precedence as {@link #WORK_STATUS_NAME_CASE}, but as a 0-4 ordinal in the
     * {@code WORK_PRIORITY} sort's own display order (section 4.2 step 1): {@code DECISION_REQUIRED,
     * ON_HOLD, REVIEW_INPUT, NO_TRANSFER_OPTION, COMPLETED} -- a different order from the enum's own
     * declaration, so this is intentionally a distinct expression rather than {@code CASE ... THEN 0
     * WHEN name='ON_HOLD' THEN 1 ...} over {@link #WORK_STATUS_NAME_CASE}'s result (JPQL cannot
     * nest a CASE result into another CASE's WHEN condition without repeating the subqueries anyway).
     */
    static final String WORK_STATUS_SORT_ORDINAL =
            "CASE "
            + "WHEN " + UNDECIDED_EXECUTABLE_CANDIDATE_EXISTS + " THEN 0 "
            + "WHEN " + HELD_EXECUTABLE_CANDIDATE_EXISTS + " THEN 1 "
            + "WHEN NOT (" + EXECUTABLE_CANDIDATE_EXISTS + ") AND " + REVIEW_OR_NON_ACTIONABLE + " THEN 2 "
            + "WHEN NOT (" + EXECUTABLE_CANDIDATE_EXISTS + ") THEN 3 "
            + "ELSE 4 END";

    static final String SEVERITY_ORDINAL =
            "CASE WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.CRITICAL THEN 0 "
            + "WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.HIGH THEN 1 "
            + "WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.REVIEW THEN 2 "
            + "ELSE 3 END";

    static final String CONFIDENCE_ORDINAL =
            "CASE WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.HIGH THEN 0 "
            + "WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.MEDIUM THEN 1 "
            + "WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.LOW THEN 2 "
            + "WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.NONE THEN 3 "
            + "ELSE 4 END";

    /** The exact D-1 selling price scalar subquery every per-row {@code estimatedSalesImpact} also uses. */
    static final String CURRENT_SELLING_PRICE_SUBQUERY =
            "(SELECT ds.averageSellingPrice FROM com.bapegg.stockpilot.inventory.SpDailySale ds "
            + "WHERE ds.salesDate = :priceDate AND ds.storeId = snap.storeId AND ds.skuId = snap.skuId "
            + "AND ds.inputSnapshotVersion = :inputVersion)";

    static final String SALES_EXPOSURE_EXPR =
            "(m.expectedShortageQuantity * " + CURRENT_SELLING_PRICE_SUBQUERY + ")";

    static final String SALES_EXPOSURE_NULL_FLAG =
            "(CASE WHEN " + SALES_EXPOSURE_EXPR + " IS NULL THEN 1 ELSE 0 END)";

    static final String SHORTAGE_NULL_FLAG =
            "(CASE WHEN m.expectedShortageQuantity IS NULL THEN 1 ELSE 0 END)";

    static final String COVERAGE_NULL_FLAG =
            "(CASE WHEN m.coverageDays IS NULL THEN 1 ELSE 0 END)";

    /**
     * The MVP-2 run-bound list's full row predicate, per section 4.2 as extended by the redesign
     * spec's {@code workStatus} filter (section 4.2) -- shared verbatim by every
     * {@code findPagedIds*} sort variant below and by {@code countPaged}, so the filtered row set
     * can never drift between the id-page query and its count. {@code m}/{@code snap} are the
     * aliases every including query must declare exactly as {@code FROM SpInventoryMetric m JOIN
     * m.inventorySnapshot snap}.
     */
    static final String LIST_FILTER_WHERE =
            "WHERE m.analysisRun.analysisRunId = :runId "
            + "AND m.inventoryExceptionType <> com.bapegg.stockpilot.demand.InventoryExceptionType.NORMAL "
            + "AND (:exceptionTypeFilterActive = false OR m.inventoryExceptionType IN :exceptionTypes) "
            + "AND (:severityFilterActive = false OR m.severity IN :severities) "
            + "AND (:signalFilterActive = false OR m.primaryDemandSignalType IN :signals) "
            + "AND (:confidenceFilterActive = false OR m.demandConfidence IN :confidences) "
            + "AND (:qualityFlagFilterActive = false OR EXISTS ("
            + "SELECT 1 FROM SpMetricQualityFlag f WHERE f.inventoryMetric = m AND f.flagCode IN :qualityFlags)) "
            + "AND (:storeId IS NULL OR snap.storeId = :storeId) "
            + "AND (:skuId IS NULL OR snap.skuId = :skuId) "
            + "AND (:hasExecutableCandidate IS NULL OR "
            + "(:hasExecutableCandidate = true AND " + EXECUTABLE_CANDIDATE_EXISTS + ") OR "
            + "(:hasExecutableCandidate = false AND NOT (" + EXECUTABLE_CANDIDATE_EXISTS + "))) "
            + "AND (:workStatusFilterActive = false OR (" + WORK_STATUS_NAME_CASE + ") IN :workStatusNames) ";

    /** Fixed pagination tie-breaker every sort appends last, per section 4.2. */
    static final String TIE_BREAKER = "snap.storeId ASC, snap.skuId ASC, m.inventoryMetricId ASC";

    private static final String WORK_PRIORITY_COLUMNS_ASC =
            WORK_STATUS_SORT_ORDINAL + " ASC, " + SEVERITY_ORDINAL + " ASC, "
            + SALES_EXPOSURE_EXPR + " DESC NULLS LAST, m.expectedShortageQuantity DESC NULLS LAST, "
            + CONFIDENCE_ORDINAL + " ASC, ";
    private static final String WORK_PRIORITY_COLUMNS_DESC =
            WORK_STATUS_SORT_ORDINAL + " DESC, " + SEVERITY_ORDINAL + " DESC, "
            + SALES_EXPOSURE_EXPR + " ASC NULLS LAST, m.expectedShortageQuantity ASC NULLS LAST, "
            + CONFIDENCE_ORDINAL + " DESC, ";

    static final String ORDER_BY_WORK_PRIORITY_ASC = "ORDER BY " + WORK_PRIORITY_COLUMNS_ASC + TIE_BREAKER;
    static final String ORDER_BY_WORK_PRIORITY_DESC = "ORDER BY " + WORK_PRIORITY_COLUMNS_DESC + TIE_BREAKER;

    static final String ORDER_BY_SALES_EXPOSURE_ASC =
            "ORDER BY " + SALES_EXPOSURE_EXPR + " ASC NULLS LAST, " + TIE_BREAKER;
    static final String ORDER_BY_SALES_EXPOSURE_DESC =
            "ORDER BY " + SALES_EXPOSURE_EXPR + " DESC NULLS LAST, " + TIE_BREAKER;

    static final String ORDER_BY_SHORTAGE_QUANTITY_ASC =
            "ORDER BY m.expectedShortageQuantity ASC NULLS LAST, " + TIE_BREAKER;
    static final String ORDER_BY_SHORTAGE_QUANTITY_DESC =
            "ORDER BY m.expectedShortageQuantity DESC NULLS LAST, " + TIE_BREAKER;

    static final String ORDER_BY_COVERAGE_DAYS_ASC =
            "ORDER BY m.coverageDays ASC NULLS LAST, " + TIE_BREAKER;
    static final String ORDER_BY_COVERAGE_DAYS_DESC =
            "ORDER BY m.coverageDays DESC NULLS LAST, " + TIE_BREAKER;

    static final String ORDER_BY_STORE_PRODUCT_ASC = "ORDER BY snap.storeId ASC, snap.skuId ASC, m.inventoryMetricId ASC";
    static final String ORDER_BY_STORE_PRODUCT_DESC = "ORDER BY snap.storeId DESC, snap.skuId DESC, m.inventoryMetricId ASC";

    private static final String SELECT_IDS = "SELECT m.inventoryMetricId FROM SpInventoryMetric m JOIN m.inventorySnapshot snap ";

    static final String FIND_PAGED_IDS_WORK_PRIORITY_ASC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_WORK_PRIORITY_ASC;
    static final String FIND_PAGED_IDS_WORK_PRIORITY_DESC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_WORK_PRIORITY_DESC;
    static final String FIND_PAGED_IDS_SALES_EXPOSURE_ASC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_SALES_EXPOSURE_ASC;
    static final String FIND_PAGED_IDS_SALES_EXPOSURE_DESC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_SALES_EXPOSURE_DESC;
    static final String FIND_PAGED_IDS_SHORTAGE_QUANTITY_ASC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_SHORTAGE_QUANTITY_ASC;
    static final String FIND_PAGED_IDS_SHORTAGE_QUANTITY_DESC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_SHORTAGE_QUANTITY_DESC;
    static final String FIND_PAGED_IDS_COVERAGE_DAYS_ASC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_COVERAGE_DAYS_ASC;
    static final String FIND_PAGED_IDS_COVERAGE_DAYS_DESC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_COVERAGE_DAYS_DESC;
    static final String FIND_PAGED_IDS_STORE_PRODUCT_ASC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_STORE_PRODUCT_ASC;
    static final String FIND_PAGED_IDS_STORE_PRODUCT_DESC = SELECT_IDS + LIST_FILTER_WHERE + ORDER_BY_STORE_PRODUCT_DESC;

    static final String COUNT_PAGED = "SELECT COUNT(m) FROM SpInventoryMetric m JOIN m.inventorySnapshot snap " + LIST_FILTER_WHERE;

    /**
     * The run-wide, filter-independent {@code AllocatorWorkSummary} aggregate, per section 4.4.
     * One conditional-aggregate statement over every non-NORMAL metric in the run -- never loads
     * metric entities into memory. The five work-status counts are computed with the same
     * {@link #WORK_STATUS_NAME_CASE} precedence the per-row {@code workStatus} field uses.
     */
    static final String SUMMARY =
            "SELECT COUNT(m), "
            + "SUM(CASE WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.CRITICAL THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (" + WORK_STATUS_NAME_CASE + ") = 'DECISION_REQUIRED' THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (" + WORK_STATUS_NAME_CASE + ") = 'ON_HOLD' THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (" + WORK_STATUS_NAME_CASE + ") = 'REVIEW_INPUT' THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (" + WORK_STATUS_NAME_CASE + ") = 'NO_TRANSFER_OPTION' THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (" + WORK_STATUS_NAME_CASE + ") = 'COMPLETED' THEN 1 ELSE 0 END), "
            + "COALESCE(SUM(" + SALES_EXPOSURE_EXPR + "), 0), "
            + "SUM(CASE WHEN m.expectedShortageQuantity > 0 AND (" + SALES_EXPOSURE_EXPR + ") IS NULL THEN 1 ELSE 0 END) "
            + "FROM SpInventoryMetric m JOIN m.inventorySnapshot snap "
            + "WHERE m.analysisRun.analysisRunId = :runId "
            + "AND m.inventoryExceptionType <> com.bapegg.stockpilot.demand.InventoryExceptionType.NORMAL";
}
