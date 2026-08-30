package com.bapegg.stockpilot.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Stable SYNTHETIC reference data. Rows are loaded by Flyway Seed migrations; the
 * application does not write to this table.
 */
@Entity
@Table(name = "sp_product")
public class SpProduct {

    @Id
    @Column(name = "sku_id", length = 64)
    private String skuId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "color", nullable = false, length = 50)
    private String color;

    @Column(name = "size_name", nullable = false, length = 30)
    private String sizeName;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpProduct() {
    }

    public String getSkuId() {
        return skuId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public String getColor() {
        return color;
    }

    public String getSizeName() {
        return sizeName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
