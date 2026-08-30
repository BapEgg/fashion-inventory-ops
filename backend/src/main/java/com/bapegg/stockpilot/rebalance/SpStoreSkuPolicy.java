package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One store-SKU inventory policy (display minimum, safety stock, capacity, coverage/retained
 * days), per {@code V6}'s {@code sp_store_sku_policy}. The approval transaction re-reads both
 * the receiver's and the donor's policy for the requested {@code inputSnapshotVersion}.
 */
@Entity
@Table(name = "sp_store_sku_policy")
public class SpStoreSkuPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_sku_policy_id")
    private Long storeSkuPolicyId;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "display_minimum", nullable = false)
    private int displayMinimum;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Column(name = "maximum_capacity", nullable = false)
    private int maximumCapacity;

    @Column(name = "target_coverage_days", nullable = false)
    private int targetCoverageDays;

    @Column(name = "retained_days", nullable = false)
    private int retainedDays;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "assumption_type", nullable = false, length = 20)
    private String assumptionType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpStoreSkuPolicy() {
    }

    public SpStoreSkuPolicy(
            String storeId,
            String skuId,
            int displayMinimum,
            int safetyStock,
            int maximumCapacity,
            int targetCoverageDays,
            int retainedDays,
            String inputSnapshotVersion) {
        this.storeId = storeId;
        this.skuId = skuId;
        this.displayMinimum = displayMinimum;
        this.safetyStock = safetyStock;
        this.maximumCapacity = maximumCapacity;
        this.targetCoverageDays = targetCoverageDays;
        this.retainedDays = retainedDays;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.assumptionType = "ASSUMPTION";
    }

    public Long getStoreSkuPolicyId() {
        return storeSkuPolicyId;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getSkuId() {
        return skuId;
    }

    public int getDisplayMinimum() {
        return displayMinimum;
    }

    public int getSafetyStock() {
        return safetyStock;
    }

    public int getMaximumCapacity() {
        return maximumCapacity;
    }

    public int getTargetCoverageDays() {
        return targetCoverageDays;
    }

    public int getRetainedDays() {
        return retainedDays;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
