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
 * One planned or confirmed inbound shipment, per {@code V6}'s {@code sp_inbound_schedule}.
 * The approval transaction only ever counts {@link InboundStatus#CONFIRMED} rows, per
 * business-rules.md section 10's "already-covers" evidence.
 */
@Entity
@Table(name = "sp_inbound_schedule")
public class SpInboundSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inbound_schedule_id")
    private Long inboundScheduleId;

    @Column(name = "inbound_reference", nullable = false, length = 64)
    private String inboundReference;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "eta_at")
    private OffsetDateTime etaAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_status", nullable = false, length = 20)
    private InboundStatus inboundStatus;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpInboundSchedule() {
    }

    public SpInboundSchedule(
            String inboundReference,
            String storeId,
            String skuId,
            Integer quantity,
            OffsetDateTime etaAt,
            InboundStatus inboundStatus,
            String inputSnapshotVersion) {
        this.inboundReference = inboundReference;
        this.storeId = storeId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.etaAt = etaAt;
        this.inboundStatus = inboundStatus;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.sourceType = "SYNTHETIC";
    }

    public Long getInboundScheduleId() {
        return inboundScheduleId;
    }

    public String getInboundReference() {
        return inboundReference;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public OffsetDateTime getEtaAt() {
        return etaAt;
    }

    public InboundStatus getInboundStatus() {
        return inboundStatus;
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
