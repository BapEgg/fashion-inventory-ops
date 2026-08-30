package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
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
 * One rejection reason for a {@link SpRebalanceRecommendation} candidate, per {@code V6}'s
 * {@code sp_candidate_reason} and {@code knowledge/business-rules.md} section 7: every
 * applicable reason is stored, not just the representative one.
 */
@Entity
@Table(name = "sp_candidate_reason")
public class SpCandidateReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "candidate_reason_id")
    private Long candidateReasonId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private SpRebalanceRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private TransferCandidateRejectionReason reasonCode;

    @Column(name = "reason_order", nullable = false)
    private int reasonOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpCandidateReason() {
    }

    public SpCandidateReason(
            SpRebalanceRecommendation recommendation, TransferCandidateRejectionReason reasonCode, int reasonOrder) {
        this.recommendation = recommendation;
        this.reasonCode = reasonCode;
        this.reasonOrder = reasonOrder;
    }

    public Long getCandidateReasonId() {
        return candidateReasonId;
    }

    public SpRebalanceRecommendation getRecommendation() {
        return recommendation;
    }

    public TransferCandidateRejectionReason getReasonCode() {
        return reasonCode;
    }

    public int getReasonOrder() {
        return reasonOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
