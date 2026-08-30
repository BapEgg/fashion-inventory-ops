package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.demand.TransferRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One donor-receiver transfer route, per {@code V6}'s {@code sp_store_transfer_route}.
 * {@link SpRebalanceRecommendation#getRouteId()} is a plain FK id (no relation), so the
 * approval transaction re-reads the active route directly by id via
 * {@link SpStoreTransferRouteRepository#findById}.
 */
@Entity
@Table(name = "sp_store_transfer_route")
public class SpStoreTransferRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "donor_store_id", nullable = false, length = 64)
    private String donorStoreId;

    @Column(name = "receiver_store_id", nullable = false, length = 64)
    private String receiverStoreId;

    @Column(name = "active_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String activeFlag;

    @Column(name = "owner_override_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String ownerOverrideFlag;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "minimum_quantity", nullable = false)
    private int minimumQuantity;

    @Column(name = "package_multiple", nullable = false)
    private int packageMultiple;

    @Column(name = "maximum_quantity", nullable = false)
    private int maximumQuantity;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "assumption_type", nullable = false, length = 20)
    private String assumptionType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpStoreTransferRoute() {
    }

    public SpStoreTransferRoute(
            String donorStoreId,
            String receiverStoreId,
            boolean active,
            boolean ownerOverride,
            int leadTimeDays,
            int minimumQuantity,
            int packageMultiple,
            int maximumQuantity,
            String inputSnapshotVersion) {
        this.donorStoreId = donorStoreId;
        this.receiverStoreId = receiverStoreId;
        this.activeFlag = active ? "Y" : "N";
        this.ownerOverrideFlag = ownerOverride ? "Y" : "N";
        this.leadTimeDays = leadTimeDays;
        this.minimumQuantity = minimumQuantity;
        this.packageMultiple = packageMultiple;
        this.maximumQuantity = maximumQuantity;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.assumptionType = "ASSUMPTION";
    }

    /** Converts to the pure-Java {@link TransferRoute} record the {@code demand} package calculates with. */
    public TransferRoute toTransferRoute() {
        return new TransferRoute(isActive(), isOwnerOverride(), leadTimeDays, minimumQuantity, packageMultiple, maximumQuantity);
    }

    public Long getRouteId() {
        return routeId;
    }

    public String getDonorStoreId() {
        return donorStoreId;
    }

    public String getReceiverStoreId() {
        return receiverStoreId;
    }

    public boolean isActive() {
        return "Y".equals(activeFlag);
    }

    public boolean isOwnerOverride() {
        return "Y".equals(ownerOverrideFlag);
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public int getMinimumQuantity() {
        return minimumQuantity;
    }

    public int getPackageMultiple() {
        return packageMultiple;
    }

    public int getMaximumQuantity() {
        return maximumQuantity;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
