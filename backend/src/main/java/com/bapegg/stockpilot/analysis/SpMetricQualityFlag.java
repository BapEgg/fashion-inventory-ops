package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.MetricQualityFlag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One quality flag attached to a {@link SpInventoryMetric}, per {@code V6}'s
 * {@code sp_metric_quality_flag}. Multiple rows per metric are normal (e.g. both
 * {@code OOS_CENSORED} and {@code STALE_INVENTORY} on the same store-SKU).
 */
@Entity
@Table(name = "sp_metric_quality_flag")
public class SpMetricQualityFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_quality_flag_id")
    private Long metricQualityFlagId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_metric_id", nullable = false)
    private SpInventoryMetric inventoryMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_code", nullable = false, length = 30)
    private MetricQualityFlag flagCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpMetricQualityFlag() {
    }

    public SpMetricQualityFlag(SpInventoryMetric inventoryMetric, MetricQualityFlag flagCode) {
        this.inventoryMetric = inventoryMetric;
        this.flagCode = flagCode;
    }

    public Long getMetricQualityFlagId() {
        return metricQualityFlagId;
    }

    public SpInventoryMetric getInventoryMetric() {
        return inventoryMetric;
    }

    public MetricQualityFlag getFlagCode() {
        return flagCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
