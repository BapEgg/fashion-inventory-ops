package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One in-flight inter-store transfer not yet received, per {@code V6}'s
 * {@code sp_open_transfer}. The approval transaction reads {@code APPROVED}/
 * {@code IN_TRANSIT} rows as already-committed inbound/outbound evidence, and checks for
 * any {@code REQUESTED}/{@code APPROVED}/{@code IN_TRANSIT} row on the exact same
 * donor-receiver-SKU triple as a pending-transfer conflict.
 */
@Entity
@Table(name = "sp_open_transfer")
public class SpOpenTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "open_transfer_id")
    private Long openTransferId;

    @Column(name = "transfer_reference", nullable = false, length = 64)
    private String transferReference;

    @Column(name = "donor_store_id", nullable = false, length = 64)
    private String donorStoreId;

    @Column(name = "receiver_store_id", nullable = false, length = 64)
    private String receiverStoreId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "eta_at")
    private OffsetDateTime etaAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private OpenTransferStatus transferStatus;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpOpenTransfer() {
    }

    public SpOpenTransfer(
            String transferReference,
            String donorStoreId,
            String receiverStoreId,
            String skuId,
            int quantity,
            OffsetDateTime etaAt,
            OpenTransferStatus transferStatus,
            String inputSnapshotVersion) {
        this.transferReference = transferReference;
        this.donorStoreId = donorStoreId;
        this.receiverStoreId = receiverStoreId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.etaAt = etaAt;
        this.transferStatus = transferStatus;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.sourceType = "SYNTHETIC";
    }

    public Long getOpenTransferId() {
        return openTransferId;
    }

    public String getTransferReference() {
        return transferReference;
    }

    public String getDonorStoreId() {
        return donorStoreId;
    }

    public String getReceiverStoreId() {
        return receiverStoreId;
    }

    public String getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public OffsetDateTime getEtaAt() {
        return etaAt;
    }

    public OpenTransferStatus getTransferStatus() {
        return transferStatus;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
