package com.bapegg.stockpilot.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable SYNTHETIC store-SKU inventory evidence captured for a date.
 * Rows are loaded by Flyway Seed migrations; the application does not write to this table.
 * <p>
 * {@code snapshotAt}, {@code outOfStockFlag} and {@code inputSnapshotVersion} were added
 * by {@code V6} for MVP-2; they are read-only here (DB {@code DEFAULT}s populate them on
 * insert, exactly as before this mapping existed) since no code path writes a new row
 * through this entity yet -- versioned MVP-2 snapshot ingestion is separate, later work.
 */
@Entity
@Table(name = "sp_inventory_snapshot")
public class SpInventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_snapshot_id")
    private Long inventorySnapshotId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "on_hand_quantity", nullable = false)
    private Integer onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "snapshot_at", insertable = false, updatable = false)
    private OffsetDateTime snapshotAt;

    @Column(name = "out_of_stock_flag", insertable = false, updatable = false, columnDefinition = "CHAR(1 CHAR)")
    private String outOfStockFlag;

    @Column(name = "input_snapshot_version", insertable = false, updatable = false, length = 64)
    private String inputSnapshotVersion;

    protected SpInventorySnapshot() {
    }

    public Long getInventorySnapshotId() {
        return inventorySnapshotId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getOnHandQuantity() {
        return onHandQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public String getSourceType() {
        return sourceType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getSnapshotAt() {
        return snapshotAt;
    }

    public boolean isOutOfStock() {
        return "Y".equals(outOfStockFlag);
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }
}
