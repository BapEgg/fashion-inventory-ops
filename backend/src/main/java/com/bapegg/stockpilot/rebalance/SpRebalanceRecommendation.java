package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Deterministic inter-store transfer recommendation between a receiver metric
 * (stockout risk) and a donor metric (overstock) for the same SKU and analysis run.
 */
@Entity
@Table(name = "sp_rebalance_recommendation")
public class SpRebalanceRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_metric_id", nullable = false)
    private SpInventoryMetric receiverMetric;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_metric_id", nullable = false)
    private SpInventoryMetric donorMetric;

    @Column(name = "receiver_shortage_quantity", nullable = false)
    private Integer receiverShortageQuantity;

    @Column(name = "donor_transferable_quantity", nullable = false)
    private Integer donorTransferableQuantity;

    @Column(name = "recommended_quantity", nullable = false)
    private Integer recommendedQuantity;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpRebalanceRecommendation() {
    }

    public SpRebalanceRecommendation(
            SpInventoryMetric receiverMetric, SpInventoryMetric donorMetric, RebalanceCalculation calculation) {
        this.receiverMetric = receiverMetric;
        this.donorMetric = donorMetric;
        this.receiverShortageQuantity = calculation.receiverShortageQuantity();
        this.donorTransferableQuantity = calculation.donorTransferableQuantity();
        this.recommendedQuantity = calculation.recommendedQuantity();
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public SpInventoryMetric getReceiverMetric() {
        return receiverMetric;
    }

    public SpInventoryMetric getDonorMetric() {
        return donorMetric;
    }

    public Integer getReceiverShortageQuantity() {
        return receiverShortageQuantity;
    }

    public Integer getDonorTransferableQuantity() {
        return donorTransferableQuantity;
    }

    public Integer getRecommendedQuantity() {
        return recommendedQuantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
