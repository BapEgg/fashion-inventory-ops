package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.demand.DemandEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One demand-affecting event, per {@code V6}'s {@code sp_demand_event}. Batch, approval and
 * `MANUAL` all select their representative event from these rows for a given store-SKU and
 * input version -- see {@code demand.RepresentativeEventSelection}.
 */
@Entity
@Table(name = "sp_demand_event")
public class SpDemandEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "demand_event_id")
    private Long demandEventId;

    @Column(name = "event_code", nullable = false, length = 64)
    private String eventCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private DemandEventType eventType;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "uplift_low", precision = 9, scale = 6)
    private BigDecimal upliftLow;

    @Column(name = "uplift_base", precision = 9, scale = 6)
    private BigDecimal upliftBase;

    @Column(name = "uplift_high", precision = 9, scale = 6)
    private BigDecimal upliftHigh;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "assumption_type", nullable = false, length = 20)
    private String assumptionType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpDemandEvent() {
    }

    public SpDemandEvent(
            String eventCode,
            DemandEventType eventType,
            String storeId,
            String skuId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal upliftLow,
            BigDecimal upliftBase,
            BigDecimal upliftHigh,
            String inputSnapshotVersion) {
        this.eventCode = eventCode;
        this.eventType = eventType;
        this.storeId = storeId;
        this.skuId = skuId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.upliftLow = upliftLow;
        this.upliftBase = upliftBase;
        this.upliftHigh = upliftHigh;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.sourceType = "SYNTHETIC";
        this.assumptionType = "ASSUMPTION";
    }

    /** Converts to the pure-Java {@link DemandEvent} record the {@code demand} package calculates with. */
    public DemandEvent toDemandEvent() {
        return new DemandEvent(eventCode, storeId, skuId, startDate, endDate, upliftLow, upliftBase, upliftHigh);
    }

    public Long getDemandEventId() {
        return demandEventId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public DemandEventType getEventType() {
        return eventType;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getSkuId() {
        return skuId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getUpliftLow() {
        return upliftLow;
    }

    public BigDecimal getUpliftBase() {
        return upliftBase;
    }

    public BigDecimal getUpliftHigh() {
        return upliftHigh;
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
