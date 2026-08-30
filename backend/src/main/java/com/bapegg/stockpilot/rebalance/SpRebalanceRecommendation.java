package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.SpInventoryMetric;
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
 * Deterministic inter-store transfer recommendation between a receiver metric
 * (stockout risk) and a donor metric (overstock) for the same SKU and analysis run.
 * <p>
 * {@code routeId}/{@code candidateStatus}/{@code candidateVersion}/
 * {@code recommendationMode}/{@code projectedReceiverAtArrival}/
 * {@code projectedDonorAtDispatch}/{@code receiverCapacityRemaining}/{@code evaluatedAt}
 * were added by {@code V6} for the MVP-2 candidate/scenario pipeline. {@code routeId} is
 * mapped as a plain nullable FK id, not a {@code @ManyToOne} to a
 * {@code sp_store_transfer_route} entity -- nothing in this codebase joins across that
 * relationship in Java yet, so introducing that entity is deferred until something
 * actually needs it. The MVP-1 constructor fixes {@code candidateStatus}/
 * {@code candidateVersion}/{@code recommendationMode} to the same
 * {@code ELIGIBLE}/{@code 1}/{@code RECOMMENDED} values their DB {@code DEFAULT}s already
 * supplied before these columns were mapped here.
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

    @Column(name = "receiver_shortage_quantity")
    private Integer receiverShortageQuantity;

    @Column(name = "donor_transferable_quantity")
    private Integer donorTransferableQuantity;

    @Column(name = "recommended_quantity")
    private Integer recommendedQuantity;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "route_id")
    private Long routeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_status", nullable = false, length = 20)
    private CandidateStatus candidateStatus;

    @Column(name = "candidate_version", nullable = false)
    private int candidateVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_mode", nullable = false, length = 20)
    private RecommendationMode recommendationMode;

    @Column(name = "projected_receiver_at_arrival")
    private Long projectedReceiverAtArrival;

    @Column(name = "projected_donor_at_dispatch")
    private Long projectedDonorAtDispatch;

    @Column(name = "receiver_capacity_remaining")
    private Long receiverCapacityRemaining;

    @Column(name = "evaluated_at", insertable = false, updatable = false)
    private OffsetDateTime evaluatedAt;

    protected SpRebalanceRecommendation() {
    }

    public SpRebalanceRecommendation(
            SpInventoryMetric receiverMetric, SpInventoryMetric donorMetric, RebalanceCalculation calculation) {
        this.receiverMetric = receiverMetric;
        this.donorMetric = donorMetric;
        this.receiverShortageQuantity = calculation.receiverShortageQuantity();
        this.donorTransferableQuantity = calculation.donorTransferableQuantity();
        this.recommendedQuantity = calculation.recommendedQuantity();
        this.candidateStatus = CandidateStatus.ELIGIBLE;
        this.candidateVersion = 1;
        this.recommendationMode = RecommendationMode.RECOMMENDED;
    }

    /**
     * MVP-2 candidate-evaluation factory, per {@code knowledge/business-rules.md} sections 6-7
     * and {@code data-model.md} section 6. Unlike the MVP-1 constructor, quantities are nullable
     * ({@code REJECTED}/{@code NONE} candidates, and {@code COMPARISON_ONLY}'s
     * {@code recommendedQuantity}) and {@code routeId}/candidate fields are set directly rather
     * than defaulted.
     *
     * @throws IllegalArgumentException if {@code candidateStatus} is {@code ELIGIBLE} and
     *         {@code routeId} is {@code null} -- {@code approval.CurrentApprovalBasisLoader}
     *         always resolves the active route from this id, so an eligible candidate with no
     *         route can never actually be approved; only {@code REJECTED}/{@code NONE}
     *         candidates may omit it.
     */
    public static SpRebalanceRecommendation createMvp2Candidate(
            SpInventoryMetric receiverMetric,
            SpInventoryMetric donorMetric,
            Long routeId,
            CandidateStatus candidateStatus,
            int candidateVersion,
            RecommendationMode recommendationMode,
            Integer receiverShortageQuantity,
            Integer donorTransferableQuantity,
            Integer recommendedQuantity,
            Long projectedReceiverAtArrival,
            Long projectedDonorAtDispatch,
            Long receiverCapacityRemaining) {
        if (candidateStatus == CandidateStatus.ELIGIBLE && routeId == null) {
            throw new IllegalArgumentException("routeId must not be null for an ELIGIBLE candidate.");
        }
        SpRebalanceRecommendation recommendation = new SpRebalanceRecommendation();
        recommendation.receiverMetric = receiverMetric;
        recommendation.donorMetric = donorMetric;
        recommendation.routeId = routeId;
        recommendation.candidateStatus = candidateStatus;
        recommendation.candidateVersion = candidateVersion;
        recommendation.recommendationMode = recommendationMode;
        recommendation.receiverShortageQuantity = receiverShortageQuantity;
        recommendation.donorTransferableQuantity = donorTransferableQuantity;
        recommendation.recommendedQuantity = recommendedQuantity;
        recommendation.projectedReceiverAtArrival = projectedReceiverAtArrival;
        recommendation.projectedDonorAtDispatch = projectedDonorAtDispatch;
        recommendation.receiverCapacityRemaining = receiverCapacityRemaining;
        return recommendation;
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

    public Long getRouteId() {
        return routeId;
    }

    /**
     * Sets the {@code route_id} FK this MVP-1-based constructor never populates. Approval
     * fixtures that deliberately use that legacy constructor still set the route explicitly;
     * the MVP-2 output writer uses {@link #createMvp2Candidate} instead.
     */
    public void assignRoute(Long routeId) {
        this.routeId = routeId;
    }

    public CandidateStatus getCandidateStatus() {
        return candidateStatus;
    }

    public int getCandidateVersion() {
        return candidateVersion;
    }

    public RecommendationMode getRecommendationMode() {
        return recommendationMode;
    }

    public Long getProjectedReceiverAtArrival() {
        return projectedReceiverAtArrival;
    }

    public Long getProjectedDonorAtDispatch() {
        return projectedDonorAtDispatch;
    }

    public Long getReceiverCapacityRemaining() {
        return receiverCapacityRemaining;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
