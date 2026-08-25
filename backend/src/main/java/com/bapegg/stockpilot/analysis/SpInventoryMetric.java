package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Deterministic Java-calculated inventory analysis result for one store-SKU record
 * within one {@link SpAnalysisRun}. Values are derived from an
 * {@link InventoryMetricCalculation}; storage precision matches the column scale
 * (average_daily_sales: 4 decimals, coverage_days: 2 decimals). Calculations that
 * consume these values further (e.g. rebalancing) must use the unrounded
 * {@link InventoryMetricCalculation} kept in memory during the same Batch run, not
 * these persisted, rounded columns.
 */
@Entity
@Table(name = "sp_inventory_metric")
public class SpInventoryMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_metric_id")
    private Long inventoryMetricId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private SpAnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_snapshot_id", nullable = false)
    private SpInventorySnapshot inventorySnapshot;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "average_daily_sales", nullable = false, precision = 12, scale = 4)
    private BigDecimal averageDailySales;

    @Column(name = "coverage_days", precision = 12, scale = 2)
    private BigDecimal coverageDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 30)
    private InventoryClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private InventoryPriority priority;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpInventoryMetric() {
    }

    public SpInventoryMetric(
            SpAnalysisRun analysisRun, SpInventorySnapshot inventorySnapshot, InventoryMetricCalculation calculation) {
        this.analysisRun = analysisRun;
        this.inventorySnapshot = inventorySnapshot;
        this.availableQuantity = calculation.availableQuantity();
        this.averageDailySales = calculation.averageDailySales().setScale(4, RoundingMode.HALF_UP);
        this.coverageDays = calculation.coverageDays() == null
                ? null
                : calculation.coverageDays().setScale(2, RoundingMode.HALF_UP);
        this.classification = calculation.classification();
        this.priority = calculation.priority();
    }

    public Long getInventoryMetricId() {
        return inventoryMetricId;
    }

    public SpAnalysisRun getAnalysisRun() {
        return analysisRun;
    }

    public SpInventorySnapshot getInventorySnapshot() {
        return inventorySnapshot;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public BigDecimal getAverageDailySales() {
        return averageDailySales;
    }

    public BigDecimal getCoverageDays() {
        return coverageDays;
    }

    public InventoryClassification getClassification() {
        return classification;
    }

    public InventoryPriority getPriority() {
        return priority;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
