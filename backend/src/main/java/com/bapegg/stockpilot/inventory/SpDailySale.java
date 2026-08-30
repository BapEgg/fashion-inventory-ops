package com.bapegg.stockpilot.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable SYNTHETIC daily store-SKU sales evidence.
 * Rows are loaded by Flyway Seed migrations; the application does not write to this table.
 * <p>
 * {@code transactionCount}, {@code maxTransactionQuantity}, {@code averageSellingPrice} and
 * {@code inputSnapshotVersion} were added by {@code V6} for MVP-2; they are read-only here
 * (DB {@code DEFAULT}/backfill populated them, and {@code ck_sp_sale_mvp2_detail} requires the
 * first three non-null together for any non-{@code MVP-1-LEGACY} version) since no code path
 * writes a new row through this entity yet.
 */
@Entity
@Table(name = "sp_daily_sale")
public class SpDailySale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_sale_id")
    private Long dailySaleId;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "transaction_count", insertable = false, updatable = false)
    private Integer transactionCount;

    @Column(name = "max_transaction_quantity", insertable = false, updatable = false)
    private Integer maxTransactionQuantity;

    @Column(name = "average_selling_price", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal averageSellingPrice;

    @Column(name = "input_snapshot_version", insertable = false, updatable = false, length = 64)
    private String inputSnapshotVersion;

    protected SpDailySale() {
    }

    public Long getDailySaleId() {
        return dailySaleId;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getSoldQuantity() {
        return soldQuantity;
    }

    public String getSourceType() {
        return sourceType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getTransactionCount() {
        return transactionCount;
    }

    public Integer getMaxTransactionQuantity() {
        return maxTransactionQuantity;
    }

    public BigDecimal getAverageSellingPrice() {
        return averageSellingPrice;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }
}
