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
 * Immutable SYNTHETIC daily store-SKU sales evidence.
 * Rows are loaded by Flyway Seed migrations; the application does not write to this table.
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
}
