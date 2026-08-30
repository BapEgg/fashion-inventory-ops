package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandObservationStatistics;
import com.bapegg.stockpilot.demand.DemandRateCalculation;
import com.bapegg.stockpilot.demand.DemandSignalClassification;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionClassification;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.InventorySeverity;
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

    @Column(name = "base_demand_rate", precision = 18, scale = 12)
    private BigDecimal baseDemandRate;

    @Column(name = "high_demand_rate", precision = 18, scale = 12)
    private BigDecimal highDemandRate;

    @Column(name = "observable_day_count")
    private Integer observableDayCount;

    @Column(name = "active_week_count")
    private Integer activeWeekCount;

    @Column(name = "sales_day_ratio", precision = 7, scale = 6)
    private BigDecimal salesDayRatio;

    @Column(name = "max_daily_sales")
    private Integer maxDailySales;

    @Column(name = "median_daily_sales", precision = 18, scale = 12)
    private BigDecimal medianDailySales;

    @Column(name = "mad_daily_sales", precision = 18, scale = 12)
    private BigDecimal madDailySales;

    @Column(name = "max_transaction_quantity")
    private Integer maxTransactionQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_demand_signal_type", length = 30)
    private DemandSignalType primaryDemandSignalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "demand_confidence", length = 10)
    private DemandConfidence demandConfidence;

    @Column(name = "low_demand_rate", precision = 18, scale = 12)
    private BigDecimal lowDemandRate;

    @Column(name = "projected_available")
    private Integer projectedAvailable;

    @Column(name = "expected_shortage_quantity")
    private Long expectedShortageQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_exception_type", length = 30)
    private InventoryExceptionType inventoryExceptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10)
    private InventorySeverity severity;

    @Column(name = "calculation_version", length = 32)
    private String calculationVersion;

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

    /**
     * MVP-2 Batch factory, per {@code knowledge/business-rules.md} sections 2-6 and
     * {@code data-model.md}'s Phase 3 legacy-column mapping. {@code projection} must be the
     * store-SKU's canonical projection at the earliest active-route-or-confirmed-inbound
     * cutoff; legacy {@code available_quantity}/{@code coverage_days} use only
     * {@link InventoryProjection#currentAvailable()} (the analysis-date snapshot position), never
     * the projected-forward total, so they stay comparable to the MVP-1 constructor's own legacy
     * columns. {@code expectedShortageQuantity} is stored as-is -- it must already be the finished
     * {@code max(BASE target - projectedReceiverBeforeDemand, 0)} value the calling orchestrator
     * computed; this constructor does not recompute it.
     */
    public SpInventoryMetric(
            SpAnalysisRun analysisRun,
            SpInventorySnapshot inventorySnapshot,
            DemandObservationStatistics stats,
            DemandSignalClassification signal,
            DemandRateCalculation rates,
            InventoryProjection projection,
            InventoryExceptionClassification exception,
            Long expectedShortageQuantity,
            String calculationVersion) {
        this.analysisRun = analysisRun;
        this.inventorySnapshot = inventorySnapshot;

        this.availableQuantity = projection.currentAvailable();
        this.averageDailySales = (stats.observableDayCount() > 0
                ? BigDecimal.valueOf(stats.totalWindowSales())
                        .divide(BigDecimal.valueOf(stats.observableDayCount()), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        BigDecimal baseRate = rates.baseDemandRate();
        this.coverageDays = (baseRate == null || baseRate.signum() <= 0)
                ? null
                : BigDecimal.valueOf(projection.currentAvailable()).divide(baseRate, 2, RoundingMode.HALF_UP);
        this.classification = exception.exceptionType() == InventoryExceptionType.REVIEW_REQUIRED
                ? InventoryClassification.NON_ACTIONABLE
                : InventoryClassification.valueOf(exception.exceptionType().name());
        this.priority = switch (exception.severity()) {
            case CRITICAL -> InventoryPriority.CRITICAL;
            case HIGH -> InventoryPriority.HIGH;
            case REVIEW -> null;
            case null -> null;
        };

        this.observableDayCount = stats.observableDayCount();
        this.activeWeekCount = stats.activeWeekCount();
        this.salesDayRatio = stats.salesDayRatio();
        this.maxDailySales = stats.maxDailySales();
        this.medianDailySales = stats.medianDailySales();
        this.madDailySales = stats.madDailySales();
        this.maxTransactionQuantity = stats.maxTransactionQuantityInWindow();
        this.primaryDemandSignalType = signal.signalType();
        this.demandConfidence = signal.confidence();
        this.lowDemandRate = rates.lowDemandRate();
        this.baseDemandRate = rates.baseDemandRate();
        this.highDemandRate = rates.highDemandRate();
        this.projectedAvailable = projection.projectedReceiverBeforeDemand();
        this.expectedShortageQuantity = expectedShortageQuantity;
        this.inventoryExceptionType = exception.exceptionType();
        this.severity = exception.severity();
        this.calculationVersion = calculationVersion;
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

    public BigDecimal getBaseDemandRate() {
        return baseDemandRate;
    }

    public BigDecimal getHighDemandRate() {
        return highDemandRate;
    }

    /**
     * Sets the {@code V6} {@code low/base/high_demand_rate} columns this MVP-1
     * {@link InventoryMetricCalculation}-based constructor never populates. Approval fixtures
     * that deliberately use that legacy constructor still set the rates explicitly; the MVP-2
     * output writer uses the full MVP-2 constructor instead.
     */
    public void applyDemandRates(BigDecimal baseDemandRate, BigDecimal highDemandRate) {
        this.baseDemandRate = baseDemandRate;
        this.highDemandRate = highDemandRate;
    }

    public Integer getObservableDayCount() {
        return observableDayCount;
    }

    public Integer getActiveWeekCount() {
        return activeWeekCount;
    }

    public BigDecimal getSalesDayRatio() {
        return salesDayRatio;
    }

    public Integer getMaxDailySales() {
        return maxDailySales;
    }

    public BigDecimal getMedianDailySales() {
        return medianDailySales;
    }

    public BigDecimal getMadDailySales() {
        return madDailySales;
    }

    public Integer getMaxTransactionQuantity() {
        return maxTransactionQuantity;
    }

    public DemandSignalType getPrimaryDemandSignalType() {
        return primaryDemandSignalType;
    }

    public DemandConfidence getDemandConfidence() {
        return demandConfidence;
    }

    public BigDecimal getLowDemandRate() {
        return lowDemandRate;
    }

    public Integer getProjectedAvailable() {
        return projectedAvailable;
    }

    public Long getExpectedShortageQuantity() {
        return expectedShortageQuantity;
    }

    public InventoryExceptionType getInventoryExceptionType() {
        return inventoryExceptionType;
    }

    public InventorySeverity getSeverity() {
        return severity;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }
}
