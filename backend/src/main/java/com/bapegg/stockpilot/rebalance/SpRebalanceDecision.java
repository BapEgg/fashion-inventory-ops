package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One terminal user approval or rejection audit record for a {@link SpRebalanceRecommendation}.
 * Per business-rules.md section 6, a terminal decision cannot be changed in MVP; that is
 * enforced by the unique {@code recommendation_id} constraint plus a check in
 * {@code RebalanceDecisionService} before insert.
 */
@Entity
@Table(name = "sp_rebalance_decision")
public class SpRebalanceDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false, unique = true)
    private SpRebalanceRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 20)
    private DecisionStatus decisionStatus;

    @Column(name = "selected_quantity", nullable = false)
    private Integer selectedQuantity;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "actor_label", nullable = false, length = 100)
    private String actorLabel;

    @Column(name = "decided_at", insertable = false, updatable = false)
    private OffsetDateTime decidedAt;

    protected SpRebalanceDecision() {
    }

    public SpRebalanceDecision(
            SpRebalanceRecommendation recommendation,
            DecisionStatus decisionStatus,
            int selectedQuantity,
            String reason,
            String actorLabel) {
        this.recommendation = recommendation;
        this.decisionStatus = decisionStatus;
        this.selectedQuantity = selectedQuantity;
        this.reason = reason;
        this.actorLabel = actorLabel;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public SpRebalanceRecommendation getRecommendation() {
        return recommendation;
    }

    public DecisionStatus getDecisionStatus() {
        return decisionStatus;
    }

    public Integer getSelectedQuantity() {
        return selectedQuantity;
    }

    public String getReason() {
        return reason;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
