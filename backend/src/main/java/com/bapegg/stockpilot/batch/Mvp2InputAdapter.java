package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DailyDemandObservation;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.DemandEvent;
import com.bapegg.stockpilot.demand.DemandObservationWindow;
import com.bapegg.stockpilot.demand.TransferRoute;
import com.bapegg.stockpilot.rebalance.InboundStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads one MVP-2 Batch run's complete input evidence in exactly seven bulk read groups, per
 * {@code data-model.md}'s Phase 3 mapping: anchor+catalog+policy, 28-day inventory history,
 * 28-day sales history, demand event, inbound schedule, open transfer, transfer route, and
 * active-approved-draft quantity sum. No query here runs inside a per-anchor or per-lane loop --
 * every group executes exactly once per {@link #load} call regardless of how many anchors or
 * lanes the result contains. This class never computes a statistic, signal, rate, eligibility or
 * quantity itself; it only assembles raw evidence into the shapes the pure {@code demand}
 * calculation layer already expects.
 * <p>
 * Any input-contract violation (no anchors, an incomplete or impossibly-future 28-day history, a
 * quantity too large for the domain's integer range, or an internally inconsistent raw row)
 * throws {@link InputContractViolationException} and returns nothing -- there is no partial or
 * best-effort graph.
 */
@Component
public class Mvp2InputAdapter {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.of("+09:00");

    private final JdbcTemplate jdbcTemplate;

    public Mvp2InputAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Mvp2InputGraph load(LocalDate analysisDate, String inputSnapshotVersion) {
        if (analysisDate == null) {
            throw new IllegalArgumentException("analysisDate must not be null.");
        }
        if (inputSnapshotVersion == null || inputSnapshotVersion.isBlank()) {
            throw new IllegalArgumentException("inputSnapshotVersion must not be blank.");
        }

        List<AnchorRow> anchorRows = loadAnchorRows(analysisDate, inputSnapshotVersion);
        if (anchorRows.isEmpty()) {
            throw new InputContractViolationException(
                    "No anchor snapshot rows found for analysisDate=" + analysisDate
                            + ", inputSnapshotVersion='" + inputSnapshotVersion + "'.");
        }

        // business-rules.md section 1: analysisDate + 1 day 00:00 Asia/Seoul. An anchor's own
        // current snapshot dated later than this is impossible input, not merely stale -- stale
        // (older than 24h, or a mismatched local date) stays a later STALE_INVENTORY concern.
        OffsetDateTime analysisReferenceAt = analysisDate.plusDays(1).atStartOfDay(SEOUL_OFFSET).toOffsetDateTime();

        LocalDate historyStart = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate historyEnd = analysisDate.minusDays(1);
        // No upper bound: this is still one physical statement, and letting it also see any
        // same-version row dated on/after analysisDate is exactly how a "future snapshot" is
        // detected in buildAnchor below without a ninth query.
        Map<StoreSkuKey, Map<LocalDate, InventoryHistoryRow>> inventoryHistory =
                loadInventoryHistory(inputSnapshotVersion, historyStart);
        Map<StoreSkuKey, Map<LocalDate, SalesHistoryRow>> salesHistory =
                loadSalesHistory(inputSnapshotVersion, historyStart, historyEnd);

        List<Mvp2Anchor> anchors = new ArrayList<>(anchorRows.size());
        for (AnchorRow row : anchorRows) {
            anchors.add(buildAnchor(row, analysisDate, analysisReferenceAt, historyStart,
                    inputSnapshotVersion, inventoryHistory, salesHistory));
        }

        List<DemandEvent> events = loadEvents(inputSnapshotVersion);
        List<Mvp2InboundRow> inboundSchedules = loadInboundSchedules(inputSnapshotVersion);
        List<Mvp2OpenTransferRow> openTransfers = loadOpenTransfers(inputSnapshotVersion);
        List<Mvp2Route> routes = loadRoutes(inputSnapshotVersion);
        Map<Mvp2DonorSkuKey, Long> activeApprovedDraftQuantity = loadActiveApprovedDraftQuantity();

        return new Mvp2InputGraph(
                analysisDate, inputSnapshotVersion, anchors, events,
                inboundSchedules, openTransfers, routes, activeApprovedDraftQuantity,
                groupBy(events, e -> new Mvp2StoreSkuKey(e.storeId(), e.skuId())),
                groupBy(inboundSchedules, r -> new Mvp2StoreSkuKey(r.storeId(), r.skuId())),
                groupBy(openTransfers, r -> new Mvp2LaneKey(r.donorStoreId(), r.receiverStoreId(), r.skuId())),
                groupBy(routes, r -> new Mvp2StorePairKey(r.donorStoreId(), r.receiverStoreId())));
    }

    // ---- Group 1: anchor + catalog + optional policy ----------------------------------------

    private record AnchorRow(
            String storeId, String skuId, String ownerCode, LocalDate launchDate,
            int onHandQuantity, int reservedQuantity, OffsetDateTime snapshotAt, boolean outOfStock,
            Mvp2Policy policy) {
    }

    private List<AnchorRow> loadAnchorRows(LocalDate analysisDate, String inputSnapshotVersion) {
        return jdbcTemplate.query(
                """
                SELECT s.store_id AS store_id, s.sku_id AS sku_id,
                       st.inventory_owner_code AS owner_code, p.launch_date AS launch_date,
                       s.on_hand_quantity AS on_hand_quantity, s.reserved_quantity AS reserved_quantity,
                       s.snapshot_at AS snapshot_at, s.out_of_stock_flag AS out_of_stock_flag,
                       pol.display_minimum AS display_minimum, pol.safety_stock AS safety_stock,
                       pol.maximum_capacity AS maximum_capacity, pol.target_coverage_days AS target_coverage_days,
                       pol.retained_days AS retained_days
                FROM sp_inventory_snapshot s
                JOIN sp_store st ON st.store_id = s.store_id
                JOIN sp_product p ON p.sku_id = s.sku_id
                LEFT JOIN sp_store_sku_policy pol
                    ON pol.store_id = s.store_id AND pol.sku_id = s.sku_id
                    AND pol.input_snapshot_version = s.input_snapshot_version
                WHERE s.snapshot_date = ? AND s.input_snapshot_version = ?
                """,
                (rs, rowNum) -> {
                    String storeId = rs.getString("store_id");
                    String skuId = rs.getString("sku_id");
                    String context = "store=" + storeId + ", sku=" + skuId;
                    Mvp2Policy policy = rs.getObject("display_minimum") == null
                            ? Mvp2Policy.defaults()
                            : new Mvp2Policy(
                                    safeInt(rs.getLong("display_minimum"), context, "display_minimum"),
                                    safeInt(rs.getLong("safety_stock"), context, "safety_stock"),
                                    safeInt(rs.getLong("maximum_capacity"), context, "maximum_capacity"),
                                    safeInt(rs.getLong("target_coverage_days"), context, "target_coverage_days"),
                                    safeInt(rs.getLong("retained_days"), context, "retained_days"));
                    return new AnchorRow(
                            storeId, skuId, rs.getString("owner_code"), toLocalDate(rs, "launch_date"),
                            safeInt(rs.getLong("on_hand_quantity"), context, "on_hand_quantity"),
                            safeInt(rs.getLong("reserved_quantity"), context, "reserved_quantity"),
                            toOffsetDateTime(rs, "snapshot_at"), "Y".equals(rs.getString("out_of_stock_flag")),
                            policy);
                },
                analysisDate, inputSnapshotVersion);
    }

    // ---- Group 2: 28-day inventory + sales history -------------------------------------------

    private record StoreSkuKey(String storeId, String skuId) {
    }

    private record InventoryHistoryRow(
            int onHandQuantity, int reservedQuantity, OffsetDateTime snapshotAt, boolean outOfStock) {
    }

    private record SalesHistoryRow(int soldQuantity, int transactionCount, int maxTransactionQuantity) {
    }

    /** No upper date bound -- see the caller's note on why that is still one statement. */
    private Map<StoreSkuKey, Map<LocalDate, InventoryHistoryRow>> loadInventoryHistory(
            String inputSnapshotVersion, LocalDate historyStart) {
        Map<StoreSkuKey, Map<LocalDate, InventoryHistoryRow>> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT store_id, sku_id, snapshot_date, on_hand_quantity, reserved_quantity, snapshot_at, out_of_stock_flag
                FROM sp_inventory_snapshot
                WHERE input_snapshot_version = ? AND snapshot_date >= ?
                """,
                rs -> {
                    String storeId = rs.getString("store_id");
                    String skuId = rs.getString("sku_id");
                    String context = "store=" + storeId + ", sku=" + skuId;
                    LocalDate date = toLocalDate(rs, "snapshot_date");
                    InventoryHistoryRow row = new InventoryHistoryRow(
                            safeInt(rs.getLong("on_hand_quantity"), context, "on_hand_quantity"),
                            safeInt(rs.getLong("reserved_quantity"), context, "reserved_quantity"),
                            toOffsetDateTime(rs, "snapshot_at"), "Y".equals(rs.getString("out_of_stock_flag")));
                    result.computeIfAbsent(new StoreSkuKey(storeId, skuId), k -> new LinkedHashMap<>()).put(date, row);
                },
                inputSnapshotVersion, historyStart);
        return result;
    }

    private Map<StoreSkuKey, Map<LocalDate, SalesHistoryRow>> loadSalesHistory(
            String inputSnapshotVersion, LocalDate historyStart, LocalDate historyEnd) {
        Map<StoreSkuKey, Map<LocalDate, SalesHistoryRow>> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT store_id, sku_id, sales_date, sold_quantity, transaction_count, max_transaction_quantity
                FROM sp_daily_sale
                WHERE input_snapshot_version = ? AND sales_date >= ? AND sales_date <= ?
                """,
                rs -> {
                    String storeId = rs.getString("store_id");
                    String skuId = rs.getString("sku_id");
                    String context = "store=" + storeId + ", sku=" + skuId;
                    LocalDate date = toLocalDate(rs, "sales_date");
                    if (rs.getObject("transaction_count") == null || rs.getObject("max_transaction_quantity") == null) {
                        throw new InputContractViolationException("Sales row for " + context
                                + ", date=" + date + " is missing transaction_count/max_transaction_quantity for "
                                + "input_snapshot_version='" + inputSnapshotVersion + "'.");
                    }
                    SalesHistoryRow row = new SalesHistoryRow(
                            safeInt(rs.getLong("sold_quantity"), context, "sold_quantity"),
                            safeInt(rs.getLong("transaction_count"), context, "transaction_count"),
                            safeInt(rs.getLong("max_transaction_quantity"), context, "max_transaction_quantity"));
                    result.computeIfAbsent(new StoreSkuKey(storeId, skuId), k -> new LinkedHashMap<>()).put(date, row);
                },
                inputSnapshotVersion, historyStart, historyEnd);
        return result;
    }

    private Mvp2Anchor buildAnchor(
            AnchorRow anchorRow, LocalDate analysisDate, OffsetDateTime analysisReferenceAt,
            LocalDate historyStart, String inputSnapshotVersion,
            Map<StoreSkuKey, Map<LocalDate, InventoryHistoryRow>> inventoryHistory,
            Map<StoreSkuKey, Map<LocalDate, SalesHistoryRow>> salesHistory) {
        String context = "store=" + anchorRow.storeId() + ", sku=" + anchorRow.skuId();
        if (anchorRow.snapshotAt() != null && anchorRow.snapshotAt().isAfter(analysisReferenceAt)) {
            throw new InputContractViolationException(
                    "Anchor " + context + " has an impossible current snapshot_at=" + anchorRow.snapshotAt()
                            + " after analysisReferenceAt=" + analysisReferenceAt + ".");
        }

        StoreSkuKey key = new StoreSkuKey(anchorRow.storeId(), anchorRow.skuId());
        Map<LocalDate, InventoryHistoryRow> inventoryByDate = inventoryHistory.getOrDefault(key, Map.of());
        Map<LocalDate, SalesHistoryRow> salesByDate = salesHistory.getOrDefault(key, Map.of());

        LocalDate futureInventoryDate = inventoryByDate.keySet().stream()
                .filter(date -> date.isAfter(analysisDate))
                .findFirst().orElse(null);
        if (futureInventoryDate != null) {
            throw new InputContractViolationException(
                    "Anchor " + context + " has an impossible inventory snapshot dated " + futureInventoryDate
                            + ", after analysisDate=" + analysisDate + ", for input_snapshot_version='"
                            + inputSnapshotVersion + "'.");
        }

        List<DailyDemandObservation> days = new ArrayList<>(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int offset = 0; offset < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; offset++) {
            LocalDate date = historyStart.plusDays(offset);
            InventoryHistoryRow inventoryRow = inventoryByDate.get(date);
            SalesHistoryRow salesRow = salesByDate.get(date);
            if (inventoryRow == null || salesRow == null) {
                throw new InputContractViolationException(
                        "Anchor " + context + " is missing its " + date + " "
                                + (inventoryRow == null ? "inventory" : "sales")
                                + " row for input_snapshot_version='" + inputSnapshotVersion + "'.");
            }
            try {
                days.add(new DailyDemandObservation(
                        date, inventoryRow.onHandQuantity(), inventoryRow.reservedQuantity(),
                        salesRow.soldQuantity(), salesRow.transactionCount(), salesRow.maxTransactionQuantity(),
                        inventoryRow.outOfStock(), inventoryRow.snapshotAt()));
            } catch (IllegalArgumentException e) {
                throw new InputContractViolationException(
                        "Anchor " + context + ", date=" + date
                                + " has an internally inconsistent raw row: " + e.getMessage(), e);
            }
        }

        DemandObservationWindow window;
        try {
            window = new DemandObservationWindow(analysisDate, anchorRow.launchDate(), days);
        } catch (IllegalArgumentException e) {
            throw new InputContractViolationException(
                    "Anchor " + context + " does not have a contract-valid 28-day observation window: "
                            + e.getMessage(), e);
        }

        return new Mvp2Anchor(
                anchorRow.storeId(), anchorRow.skuId(), anchorRow.ownerCode(),
                anchorRow.onHandQuantity(), anchorRow.reservedQuantity(), anchorRow.snapshotAt(), anchorRow.outOfStock(),
                window, anchorRow.policy());
    }

    // ---- Group 3: demand event -----------------------------------------------------------------

    private List<DemandEvent> loadEvents(String inputSnapshotVersion) {
        return jdbcTemplate.query(
                """
                SELECT event_code, store_id, sku_id, start_date, end_date, uplift_low, uplift_base, uplift_high
                FROM sp_demand_event
                WHERE input_snapshot_version = ?
                """,
                (rs, rowNum) -> new DemandEvent(
                        rs.getString("event_code"), rs.getString("store_id"), rs.getString("sku_id"),
                        toLocalDate(rs, "start_date"), toLocalDate(rs, "end_date"),
                        rs.getBigDecimal("uplift_low"), rs.getBigDecimal("uplift_base"), rs.getBigDecimal("uplift_high")),
                inputSnapshotVersion);
    }

    // ---- Group 4: inbound schedule --------------------------------------------------------------

    private List<Mvp2InboundRow> loadInboundSchedules(String inputSnapshotVersion) {
        return jdbcTemplate.query(
                """
                SELECT store_id, sku_id, quantity, eta_at, inbound_status
                FROM sp_inbound_schedule
                WHERE input_snapshot_version = ?
                """,
                (rs, rowNum) -> {
                    String storeId = rs.getString("store_id");
                    String skuId = rs.getString("sku_id");
                    Integer quantity = rs.getObject("quantity") == null
                            ? null
                            : Integer.valueOf(safeInt(rs.getLong("quantity"),
                                    "store=" + storeId + ", sku=" + skuId, "quantity"));
                    return new Mvp2InboundRow(
                            storeId, skuId, quantity, toOffsetDateTime(rs, "eta_at"),
                            InboundStatus.valueOf(rs.getString("inbound_status")));
                },
                inputSnapshotVersion);
    }

    // ---- Group 5: open transfer -----------------------------------------------------------------

    private List<Mvp2OpenTransferRow> loadOpenTransfers(String inputSnapshotVersion) {
        return jdbcTemplate.query(
                """
                SELECT donor_store_id, receiver_store_id, sku_id, quantity, transfer_status
                FROM sp_open_transfer
                WHERE input_snapshot_version = ?
                """,
                (rs, rowNum) -> {
                    String donorStoreId = rs.getString("donor_store_id");
                    String receiverStoreId = rs.getString("receiver_store_id");
                    String skuId = rs.getString("sku_id");
                    String context = "donor=" + donorStoreId + ", receiver=" + receiverStoreId + ", sku=" + skuId;
                    return new Mvp2OpenTransferRow(
                            donorStoreId, receiverStoreId, skuId,
                            safeInt(rs.getLong("quantity"), context, "quantity"),
                            OpenTransferStatus.valueOf(rs.getString("transfer_status")));
                },
                inputSnapshotVersion);
    }

    // ---- Group 6: active transfer route ---------------------------------------------------------

    private List<Mvp2Route> loadRoutes(String inputSnapshotVersion) {
        return jdbcTemplate.query(
                """
                SELECT route_id, donor_store_id, receiver_store_id, active_flag, owner_override_flag,
                       lead_time_days, minimum_quantity, package_multiple, maximum_quantity
                FROM sp_store_transfer_route
                WHERE input_snapshot_version = ?
                """,
                (rs, rowNum) -> {
                    String donorStoreId = rs.getString("donor_store_id");
                    String receiverStoreId = rs.getString("receiver_store_id");
                    String context = "donor=" + donorStoreId + ", receiver=" + receiverStoreId;
                    return new Mvp2Route(
                            rs.getLong("route_id"), donorStoreId, receiverStoreId,
                            new TransferRoute(
                                    "Y".equals(rs.getString("active_flag")), "Y".equals(rs.getString("owner_override_flag")),
                                    safeInt(rs.getLong("lead_time_days"), context, "lead_time_days"),
                                    safeInt(rs.getLong("minimum_quantity"), context, "minimum_quantity"),
                                    safeInt(rs.getLong("package_multiple"), context, "package_multiple"),
                                    safeInt(rs.getLong("maximum_quantity"), context, "maximum_quantity")));
                },
                inputSnapshotVersion);
    }

    // ---- Group 7: active APPROVED draft quantity sum ----------------------------------------------

    private Map<Mvp2DonorSkuKey, Long> loadActiveApprovedDraftQuantity() {
        Map<Mvp2DonorSkuKey, Long> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT d.donor_store_id AS donor_store_id, d.sku_id AS sku_id, SUM(d.quantity) AS total_quantity
                FROM sp_transfer_draft d
                JOIN sp_rebalance_decision dec ON dec.decision_id = d.decision_id
                WHERE d.draft_status IN ('CREATED', 'READY', 'SENT', 'ACCEPTED') AND dec.decision_status = 'APPROVED'
                GROUP BY d.donor_store_id, d.sku_id
                """,
                rs -> {
                    String donorStoreId = rs.getString("donor_store_id");
                    String skuId = rs.getString("sku_id");
                    long total = rs.getLong("total_quantity");
                    // The pure InventoryProjection this feeds requires alreadyApprovedDraftQuantity
                    // as an int; validated here (even though the map keeps the wider Long) so an
                    // aggregate that itself overflows never reaches that boundary silently.
                    safeInt(total, "donor=" + donorStoreId + ", sku=" + skuId, "activeApprovedDraftQuantity");
                    result.put(new Mvp2DonorSkuKey(donorStoreId, skuId), total);
                });
        return result;
    }

    // ---- Shared mapping helpers -----------------------------------------------------------------

    private static int safeInt(long value, String context, String fieldName) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new InputContractViolationException(
                    context + ": " + fieldName + "=" + value + " does not fit the domain's 32-bit integer range.");
        }
        return (int) value;
    }

    private static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<T, K> keyFunction) {
        Map<K, List<T>> result = new LinkedHashMap<>();
        for (T item : items) {
            result.computeIfAbsent(keyFunction.apply(item), k -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    /**
     * Reads a {@code TIMESTAMP(6) WITH TIME ZONE} column via the driver's native JDBC 4.2
     * {@code getObject(column, OffsetDateTime.class)} conversion, which preserves the actual
     * stored offset (e.g. the V6 {@code Asia/Seoul} backfill). Converting through
     * {@code java.sql.Timestamp} first would silently relabel that offset as the JVM/UTC zone,
     * shifting {@code toLocalDate()} onto the wrong calendar day for any downstream
     * stale-inventory comparison -- this must stay a direct {@code OffsetDateTime} read.
     */
    private static OffsetDateTime toOffsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
