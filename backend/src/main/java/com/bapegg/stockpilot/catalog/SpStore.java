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
@Table(name = "sp_store")
public class SpStore {

    @Id
    @Column(name = "store_id", length = 64)
    private String storeId;

    @Column(name = "store_name", nullable = false, length = 120)
    private String storeName;

    @Column(name = "region", nullable = false, length = 80)
    private String region;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpStore() {
    }

    public String getStoreId() {
        return storeId;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getRegion() {
        return region;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
