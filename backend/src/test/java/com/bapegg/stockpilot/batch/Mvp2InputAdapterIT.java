package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link Mvp2InputAdapter} against the real Oracle instance: exactly seven bulk read
 * groups (eight physical statements -- 28-day inventory and sales history are structurally
 * separate tables within one logical "history" group) execute regardless of anchor count, every
 * group's evidence lands in the graph correctly, and the input-contract failure modes from
 * {@code current-task.md} throw {@link InputContractViolationException} without partial output.
 * Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class Mvp2InputAdapterIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 10, 15);
    private static final String RECEIVER_STORE_ID = "STORE-GANGNAM";
    private static final String DONOR_STORE_ID = "STORE-HONGDAE";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.of("+09:00");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Mvp2InputAdapter adapter;

    @Test
    void fullyLoadedGraphMapsAllSevenGroupsForTwoAnchorsWithExactlyEightStatementsRegardlessOfAnchorCount() {
        String version = "MVP2-BIA-FULL";
        insertFullAnchor(RECEIVER_STORE_ID, SKU_ID, version, 5, 0);
        insertFullAnchor(DONOR_STORE_ID, SKU_ID, version, 40, 0);
        insertPolicy(RECEIVER_STORE_ID, SKU_ID, version, 2, 3, 80, 6, 10);
        // Donor gets NO policy row -- must fall back to demo defaults.
        insertEvent("EVT-BIA-1", RECEIVER_STORE_ID, SKU_ID, version,
                ANALYSIS_DATE.minusDays(1), ANALYSIS_DATE.plusDays(5), "1.2", "1.5", "1.8");
        insertInbound("INB-BIA-1", RECEIVER_STORE_ID, SKU_ID, version, 10,
                ANALYSIS_DATE.plusDays(2).atStartOfDay(SEOUL_OFFSET).toOffsetDateTime(), "CONFIRMED");
        insertOpenTransfer("OT-BIA-1", DONOR_STORE_ID, RECEIVER_STORE_ID, SKU_ID, version, 3, "APPROVED");
        Long routeId = insertRoute(DONOR_STORE_ID, RECEIVER_STORE_ID, version, true, false, 2, 1, 1, 50);
        Long approvedDecisionId = insertApprovedDecisionWithDraft(DONOR_STORE_ID, RECEIVER_STORE_ID, SKU_ID, 7);
        // A second, distinct store-SKU / lane's evidence -- proves the indexed maps separate by
        // key rather than lumping every row from the flat lists together.
        insertEvent("EVT-BIA-2", DONOR_STORE_ID, SKU_ID, version,
                ANALYSIS_DATE.minusDays(1), ANALYSIS_DATE.plusDays(5), "1.1", "1.3", "1.6");
        insertInbound("INB-BIA-2", DONOR_STORE_ID, SKU_ID, version, 4,
                ANALYSIS_DATE.plusDays(1).atStartOfDay(SEOUL_OFFSET).toOffsetDateTime(), "CONFIRMED");
        insertOpenTransfer("OT-BIA-2", RECEIVER_STORE_ID, DONOR_STORE_ID, SKU_ID, version, 1, "IN_TRANSIT");
        insertRoute(RECEIVER_STORE_ID, DONOR_STORE_ID, version, true, false, 9, 1, 1, 20);

        AtomicInteger statementCount = new AtomicInteger();
        Mvp2InputAdapter countingAdapter = new Mvp2InputAdapter(countingJdbcTemplate(statementCount));

        Mvp2InputGraph graph = countingAdapter.load(ANALYSIS_DATE, version);

        assertEquals(8, statementCount.get(),
                "Exactly eight physical statements (1 anchor+catalog+policy, 2 history [inventory,sales], "
                        + "1 event, 1 inbound, 1 open transfer, 1 route, 1 draft sum) regardless of anchor count.");

        assertEquals(ANALYSIS_DATE, graph.analysisDate());
        assertEquals(version, graph.inputSnapshotVersion());
        assertEquals(2, graph.anchors().size());

        Mvp2Anchor receiver = graph.anchors().stream()
                .filter(a -> a.storeId().equals(RECEIVER_STORE_ID)).findFirst().orElseThrow();
        assertEquals(5, receiver.currentAvailable());
        assertEquals(28, receiver.observationWindow().days().size());
        assertEquals(new Mvp2Policy(2, 3, 80, 6, 10), receiver.policy());

        Mvp2Anchor donor = graph.anchors().stream()
                .filter(a -> a.storeId().equals(DONOR_STORE_ID)).findFirst().orElseThrow();
        assertEquals(Mvp2Policy.defaults(), donor.policy(), "Missing policy row must fall back to demo defaults.");

        assertEquals(2, graph.events().size());
        DemandEvent event = graph.events().stream()
                .filter(e -> e.eventCode().equals("EVT-BIA-1")).findFirst().orElseThrow();
        assertTrue(event.hasCompleteUplift());

        assertEquals(2, graph.inboundSchedules().size());
        assertTrue(graph.inboundSchedules().stream().allMatch(Mvp2InboundRow::isComplete));

        assertEquals(2, graph.openTransfers().size());

        assertEquals(2, graph.routes().size());
        Mvp2Route donorToReceiverRoute = graph.routes().stream()
                .filter(r -> r.routeId().equals(routeId)).findFirst().orElseThrow();
        assertTrue(donorToReceiverRoute.route().active());
        assertEquals(2, donorToReceiverRoute.route().leadTimeDays());

        Long draftQuantity = graph.activeApprovedDraftQuantityByDonorSku()
                .get(new Mvp2DonorSkuKey(DONOR_STORE_ID, SKU_ID));
        assertEquals(7L, draftQuantity);
        assertTrue(approvedDecisionId > 0);

        // Indexed lookups: each key returns exactly its own evidence, not the other lane's/SKU's.
        Mvp2StoreSkuKey receiverKey = new Mvp2StoreSkuKey(RECEIVER_STORE_ID, SKU_ID);
        Mvp2StoreSkuKey donorKey = new Mvp2StoreSkuKey(DONOR_STORE_ID, SKU_ID);
        assertEquals(1, graph.eventsByStoreSku().get(receiverKey).size());
        assertEquals("EVT-BIA-1", graph.eventsByStoreSku().get(receiverKey).get(0).eventCode());
        assertEquals(1, graph.eventsByStoreSku().get(donorKey).size());
        assertEquals("EVT-BIA-2", graph.eventsByStoreSku().get(donorKey).get(0).eventCode());

        assertEquals(1, graph.inboundByStoreSku().get(receiverKey).size());
        assertEquals(10, graph.inboundByStoreSku().get(receiverKey).get(0).quantity());
        assertEquals(1, graph.inboundByStoreSku().get(donorKey).size());
        assertEquals(4, graph.inboundByStoreSku().get(donorKey).get(0).quantity());

        Mvp2LaneKey donorToReceiverLane = new Mvp2LaneKey(DONOR_STORE_ID, RECEIVER_STORE_ID, SKU_ID);
        Mvp2LaneKey receiverToDonorLane = new Mvp2LaneKey(RECEIVER_STORE_ID, DONOR_STORE_ID, SKU_ID);
        assertEquals(1, graph.openTransfersByLane().get(donorToReceiverLane).size());
        assertEquals(3, graph.openTransfersByLane().get(donorToReceiverLane).get(0).quantity());
        assertEquals(1, graph.openTransfersByLane().get(receiverToDonorLane).size());
        assertEquals(1, graph.openTransfersByLane().get(receiverToDonorLane).get(0).quantity());

        Mvp2StorePairKey donorToReceiverPair = new Mvp2StorePairKey(DONOR_STORE_ID, RECEIVER_STORE_ID);
        Mvp2StorePairKey receiverToDonorPair = new Mvp2StorePairKey(RECEIVER_STORE_ID, DONOR_STORE_ID);
        assertEquals(1, graph.routesByStorePair().get(donorToReceiverPair).size());
        assertEquals(2, graph.routesByStorePair().get(donorToReceiverPair).get(0).route().leadTimeDays());
        assertEquals(1, graph.routesByStorePair().get(receiverToDonorPair).size());
        assertEquals(9, graph.routesByStorePair().get(receiverToDonorPair).get(0).route().leadTimeDays());

        assertNull(graph.eventsByStoreSku().get(new Mvp2StoreSkuKey("STORE-NOWHERE", SKU_ID)),
                "A key with no evidence must simply be absent, not an empty-but-present entry.");

        // Both the map and every list it holds are immutable.
        assertThrows(UnsupportedOperationException.class,
                () -> graph.eventsByStoreSku().put(receiverKey, List.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.eventsByStoreSku().get(receiverKey).add(event));
        assertThrows(UnsupportedOperationException.class, () -> graph.events().add(event));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.activeApprovedDraftQuantityByDonorSku().put(new Mvp2DonorSkuKey("X", "Y"), 1L));
    }

    @Test
    void noAnchorRowsThrowsInputContractViolation() {
        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, "MVP2-BIA-NO-ANCHOR"));
        assertTrue(exception.getMessage().contains("No anchor snapshot rows found"));
    }

    @Test
    void missingOneInventoryHistoryDayThrowsInputContractViolation() {
        String version = "MVP2-BIA-GAPINV";
        LocalDate skippedDate = ANALYSIS_DATE.minusDays(10);
        insertFullAnchorSkippingOneInventoryDay(RECEIVER_STORE_ID, SKU_ID, version, skippedDate);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains(skippedDate.toString()));
        assertTrue(exception.getMessage().contains("inventory"));
    }

    @Test
    void missingOneSalesHistoryDayThrowsInputContractViolation() {
        String version = "MVP2-BIA-GAPSALE";
        LocalDate skippedDate = ANALYSIS_DATE.minusDays(15);
        insertFullAnchorSkippingOneSalesDay(RECEIVER_STORE_ID, SKU_ID, version, skippedDate);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains(skippedDate.toString()));
        assertTrue(exception.getMessage().contains("sales"));
    }

    @Test
    void aRowUnderADifferentVersionIsInvisibleAndLeavesTheDayMissing() {
        // Proves "version mixing" cannot silently substitute for a real historical day: the
        // decoy row exists in the DB but under a different input_snapshot_version, invisible to
        // the version-scoped history query, so the day is still reported missing.
        String version = "MVP2-BIA-MIXVER";
        LocalDate skippedDate = ANALYSIS_DATE.minusDays(20);
        insertFullAnchorSkippingOneInventoryDay(RECEIVER_STORE_ID, SKU_ID, version, skippedDate);
        insertInventoryDay(RECEIVER_STORE_ID, SKU_ID, skippedDate, version + "-OTHER", 20, 0);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains(skippedDate.toString()));
    }

    @Test
    void futureInventorySnapshotIsIndependentlyRejectedEvenWithACompleteHistory() {
        // A complete, otherwise-valid 28-day anchor, plus one extra same-version row dated after
        // analysisDate -- this alone (with nothing missing) must still fail.
        String version = "MVP2-BIA-FUTURE";
        insertFullAnchor(RECEIVER_STORE_ID, SKU_ID, version, 20, 0);
        LocalDate futureDate = ANALYSIS_DATE.plusDays(3);
        insertInventoryDay(RECEIVER_STORE_ID, SKU_ID, futureDate, version, 999, 0);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains(futureDate.toString()));
        assertTrue(exception.getMessage().contains("impossible"));
    }

    @Test
    void impossibleCurrentSnapshotAtAfterAnalysisReferenceAtIsRejected() {
        String version = "MVP2-BIA-REFAT";
        insertHistoryDays(RECEIVER_STORE_ID, SKU_ID, version, null);
        // analysisReferenceAt = analysisDate + 1 day 00:00 Asia/Seoul; two days later is
        // unambiguously past it, regardless of the current wall clock.
        OffsetDateTime impossibleSnapshotAt =
                ANALYSIS_DATE.plusDays(2).atStartOfDay(SEOUL_OFFSET).toOffsetDateTime();
        insertInventoryDayWithExplicitSnapshotAt(RECEIVER_STORE_ID, SKU_ID, ANALYSIS_DATE, version, 20, 0,
                impossibleSnapshotAt);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains("analysisReferenceAt"));
    }

    @Test
    void aQuantityBeyondTheIntegerRangeThrowsInputContractViolation() {
        String version = "MVP2-BIA-OVERFLOW";
        insertFullAnchor(RECEIVER_STORE_ID, SKU_ID, version, 3_000_000_000L, 0);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains("does not fit the domain's 32-bit integer range"));
    }

    @Test
    void aRouteQuantityBeyondTheIntegerRangeThrowsInputContractViolationInsteadOfADriverError() {
        String version = "MVP2-BIA-ROUTEOF";
        insertFullAnchor(RECEIVER_STORE_ID, SKU_ID, version, 20, 0);
        insertFullAnchor(DONOR_STORE_ID, SKU_ID, version, 20, 0);
        insertRoute(DONOR_STORE_ID, RECEIVER_STORE_ID, version, true, false, 1, 1, 1, 3_000_000_000L);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains("does not fit the domain's 32-bit integer range"));
    }

    @Test
    void activeApprovedDraftAggregateBeyondTheIntegerRangeThrowsInputContractViolation() {
        String version = "MVP2-BIA-DRAFTOF";
        insertFullAnchor(RECEIVER_STORE_ID, SKU_ID, version, 20, 0);
        // Each individual draft quantity fits comfortably in an int; only their SUM overflows.
        insertApprovedDecisionWithDraft(DONOR_STORE_ID, RECEIVER_STORE_ID, SKU_ID, 1_200_000_000);
        insertApprovedDecisionWithDraft(DONOR_STORE_ID, RECEIVER_STORE_ID, SKU_ID, 1_200_000_000);

        InputContractViolationException exception = assertThrows(InputContractViolationException.class,
                () -> adapter.load(ANALYSIS_DATE, version));
        assertTrue(exception.getMessage().contains("does not fit the domain's 32-bit integer range"));
    }

    // ---- fixture helpers -------------------------------------------------------------------------

    private void insertFullAnchor(String storeId, String skuId, String version, long onHand, long reserved) {
        insertHistoryDays(storeId, skuId, version, null);
        insertInventoryDay(storeId, skuId, ANALYSIS_DATE, version, onHand, reserved);
    }

    private void insertFullAnchorSkippingOneInventoryDay(
            String storeId, String skuId, String version, LocalDate skippedDate) {
        insertHistoryDays(storeId, skuId, version, skippedDate);
        insertInventoryDay(storeId, skuId, ANALYSIS_DATE, version, 20, 0);
    }

    private void insertFullAnchorSkippingOneSalesDay(String storeId, String skuId, String version, LocalDate skippedDate) {
        LocalDate historyStart = ANALYSIS_DATE.minusDays(28);
        for (int offset = 0; offset < 28; offset++) {
            LocalDate date = historyStart.plusDays(offset);
            insertInventoryDay(storeId, skuId, date, version, 20, 0);
            if (!date.equals(skippedDate)) {
                insertSalesDay(storeId, skuId, date, version, 2, 2, 1);
            }
        }
        insertInventoryDay(storeId, skuId, ANALYSIS_DATE, version, 20, 0);
    }

    private void insertHistoryDays(String storeId, String skuId, String version, LocalDate skipInventoryDate) {
        LocalDate historyStart = ANALYSIS_DATE.minusDays(28);
        for (int offset = 0; offset < 28; offset++) {
            LocalDate date = historyStart.plusDays(offset);
            if (!date.equals(skipInventoryDate)) {
                insertInventoryDay(storeId, skuId, date, version, 20, 0);
            }
            insertSalesDay(storeId, skuId, date, version, 2, 2, 1);
        }
    }

    private void insertInventoryDay(String storeId, String skuId, LocalDate date, String version, long onHand, long reserved) {
        insertInventoryDayWithExplicitSnapshotAt(
                storeId, skuId, date, version, onHand, reserved, date.atStartOfDay(SEOUL_OFFSET).toOffsetDateTime());
    }

    private void insertInventoryDayWithExplicitSnapshotAt(
            String storeId, String skuId, LocalDate date, String version, long onHand, long reserved,
            OffsetDateTime snapshotAt) {
        String outOfStockFlag = (onHand - reserved) <= 0 ? "Y" : "N";
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version, snapshot_at, out_of_stock_flag) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?)",
                date, storeId, skuId, onHand, reserved, version, snapshotAt, outOfStockFlag);
    }

    private void insertSalesDay(
            String storeId, String skuId, LocalDate date, String version,
            int soldQuantity, int transactionCount, int maxTransactionQuantity) {
        jdbcTemplate.update(
                "INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type, "
                        + "input_snapshot_version, transaction_count, max_transaction_quantity, average_selling_price) "
                        + "VALUES (?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?, 10000.00)",
                date, storeId, skuId, soldQuantity, version, transactionCount, maxTransactionQuantity);
    }

    private void insertPolicy(
            String storeId, String skuId, String version,
            int displayMinimum, int safetyStock, int maximumCapacity, int targetCoverageDays, int retainedDays) {
        jdbcTemplate.update(
                "INSERT INTO sp_store_sku_policy (store_id, sku_id, display_minimum, safety_stock, "
                        + "maximum_capacity, target_coverage_days, retained_days, input_snapshot_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                storeId, skuId, displayMinimum, safetyStock, maximumCapacity, targetCoverageDays, retainedDays, version);
    }

    private void insertEvent(
            String eventCode, String storeId, String skuId, String version,
            LocalDate start, LocalDate end, String upliftLow, String upliftBase, String upliftHigh) {
        jdbcTemplate.update(
                "INSERT INTO sp_demand_event (event_code, event_type, store_id, sku_id, start_date, end_date, "
                        + "uplift_low, uplift_base, uplift_high, input_snapshot_version) "
                        + "VALUES (?, 'PROMOTION', ?, ?, ?, ?, ?, ?, ?, ?)",
                eventCode, storeId, skuId, start, end,
                new java.math.BigDecimal(upliftLow), new java.math.BigDecimal(upliftBase), new java.math.BigDecimal(upliftHigh),
                version);
    }

    private void insertInbound(
            String reference, String storeId, String skuId, String version, int quantity,
            OffsetDateTime etaAt, String status) {
        jdbcTemplate.update(
                "INSERT INTO sp_inbound_schedule (inbound_reference, store_id, sku_id, quantity, eta_at, "
                        + "inbound_status, input_snapshot_version, source_type) VALUES (?, ?, ?, ?, ?, ?, ?, 'SYNTHETIC')",
                reference, storeId, skuId, quantity, etaAt, status, version);
    }

    private void insertOpenTransfer(
            String reference, String donorStoreId, String receiverStoreId, String skuId, String version,
            int quantity, String status) {
        jdbcTemplate.update(
                "INSERT INTO sp_open_transfer (transfer_reference, donor_store_id, receiver_store_id, sku_id, "
                        + "quantity, transfer_status, input_snapshot_version, source_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SYNTHETIC')",
                reference, donorStoreId, receiverStoreId, skuId, quantity, status, version);
    }

    private Long insertRoute(
            String donorStoreId, String receiverStoreId, String version, boolean active, boolean ownerOverride,
            int leadTimeDays, int minimumQuantity, int packageMultiple, long maximumQuantity) {
        jdbcTemplate.update(
                "INSERT INTO sp_store_transfer_route (donor_store_id, receiver_store_id, active_flag, "
                        + "owner_override_flag, lead_time_days, minimum_quantity, package_multiple, maximum_quantity, "
                        + "input_snapshot_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                donorStoreId, receiverStoreId, active ? "Y" : "N", ownerOverride ? "Y" : "N",
                leadTimeDays, minimumQuantity, packageMultiple, maximumQuantity, version);
        return jdbcTemplate.queryForObject(
                "SELECT route_id FROM sp_store_transfer_route WHERE donor_store_id = ? AND receiver_store_id = ? "
                        + "AND input_snapshot_version = ?",
                Long.class, donorStoreId, receiverStoreId, version);
    }

    private Long insertApprovedDecisionWithDraft(String donorStoreId, String receiverStoreId, String skuId, int quantity) {
        // The draft-sum group joins sp_transfer_draft to sp_rebalance_decision and is not scoped
        // to any input_snapshot_version (business-rules.md section 10) -- it reuses the existing
        // MVP-1 golden recommendation rather than building a whole new analysis run/metric pair.
        Long recommendationId = jdbcTemplate.queryForObject(
                "SELECT recommendation_id FROM sp_rebalance_recommendation WHERE ROWNUM = 1", Long.class);
        Integer maxSequence = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(decision_sequence), 0) FROM sp_rebalance_decision WHERE recommendation_id = ?",
                Integer.class, recommendationId);
        String decisionRequestId = "MVP2-BIA-" + java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sp_rebalance_decision (recommendation_id, decision_status, selected_quantity, reason, "
                        + "actor_label, decision_sequence, decision_contract_version, recommendation_version, "
                        + "decision_request_id, policy_exception_flag) "
                        + "VALUES (?, 'APPROVED', ?, 'input adapter IT fixture', 'it', ?, 'MVP-2', 1, ?, 'N')",
                recommendationId, quantity, maxSequence + 1, decisionRequestId);
        Long decisionId = jdbcTemplate.queryForObject(
                "SELECT decision_id FROM sp_rebalance_decision WHERE decision_request_id = ?", Long.class, decisionRequestId);
        jdbcTemplate.update(
                "INSERT INTO sp_transfer_draft (decision_id, donor_store_id, receiver_store_id, sku_id, quantity, "
                        + "draft_status, payload_version) VALUES (?, ?, ?, ?, ?, 'CREATED', 'MVP-2-DRAFT-V1')",
                decisionId, donorStoreId, receiverStoreId, skuId, quantity);
        return decisionId;
    }

    /** Wraps the test's own transactional connection so JdbcTemplate calls run inside the same
     * rolled-back transaction as the fixture inserts, while counting every prepareStatement/
     * createStatement call -- the "no per-anchor loop" proof required for this unit. */
    private JdbcTemplate countingJdbcTemplate(AtomicInteger counter) {
        Connection transactional = DataSourceUtils.getConnection(dataSource);
        Connection counting = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) {
                        counter.incrementAndGet();
                    }
                    try {
                        return method.invoke(transactional, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        SingleConnectionDataSource countingDataSource = new SingleConnectionDataSource(counting, true);
        return new JdbcTemplate(countingDataSource);
    }
}
