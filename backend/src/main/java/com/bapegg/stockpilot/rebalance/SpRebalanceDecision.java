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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One approval/rejection decision audit record for a {@link SpRebalanceRecommendation}.
 * <p>
 * The relationship to the recommendation is {@code @ManyToOne}, not {@code @OneToOne}:
 * {@code V6} dropped the original {@code uq_sp_dec_rec} unique constraint on
 * {@code recommendation_id} alone and replaced it with
 * {@code uq_sp_dec_rec_seq (recommendation_id, decision_sequence)}, making the decision
 * history append-only per recommendation. The repository's query methods are shaped
 * around that: {@link SpRebalanceDecisionRepository#existsByRecommendation_RecommendationId}
 * backs the MVP-1 flow's "at most one decision" rule, and
 * {@link SpRebalanceDecisionRepository#findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc}
 * reads the current decision regardless of how many rows exist -- neither can throw
 * {@code IncorrectResultSizeDataAccessException} the way a single-result
 * {@code findBy...} would, now that {@link #createMvp2Decision} is a real multi-decision
 * writer.
 * <p>
 * {@code decisionRequestId} and {@code policyException} back the section-10 approval
 * transaction contract's {@code decision_request_id}/{@code policy_exception_flag} columns
 * (added by {@code V10}), which are {@code NOT NULL} for every row including this MVP-1-only
 * path. This constructor has no caller-supplied idempotency key, so it generates one -- the
 * real client-supplied {@code Idempotency-Key} is a header on
 * {@code POST /api/rebalancing-decisions}' MVP-2 path only, carried into
 * {@link #createMvp2Decision}'s {@code decisionRequestId} parameter instead. Similarly,
 * {@code decisionSequence}/{@code decisionContractVersion}/{@code recommendationVersion}
 * (added by {@code V6}) are fixed to the same {@code 1}/{@code MVP-1}/{@code 1} values
 * their DB {@code DEFAULT}s already supplied before these columns were mapped here.
 */
@Entity
@Table(name = "sp_rebalance_decision")
public class SpRebalanceDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private SpRebalanceRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 20)
    private DecisionStatus decisionStatus;

    @Column(name = "selected_quantity")
    private Integer selectedQuantity;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "actor_label", nullable = false, length = 100)
    private String actorLabel;

    @Column(name = "decision_sequence", nullable = false)
    private int decisionSequence;

    @Column(name = "decision_contract_version", nullable = false, length = 32)
    private String decisionContractVersion;

    @Column(name = "reason_code", length = 40)
    private String reasonCode;

    @Column(name = "recommendation_version", nullable = false)
    private int recommendationVersion;

    @Column(name = "decision_request_id", nullable = false, length = 100)
    private String decisionRequestId;

    @Column(name = "policy_exception_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String policyExceptionFlag;

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
        this.decisionSequence = 1;
        this.decisionContractVersion = "MVP-1";
        this.recommendationVersion = 1;
        this.decisionRequestId = "MVP1-" + UUID.randomUUID();
        this.policyExceptionFlag = "N";
    }

    /**
     * Builds one append-only {@code MVP-2} decision row for the approval transaction --
     * the only path that can produce {@code decisionContractVersion = "MVP-2"},
     * {@code PENDING}/{@code HELD}/{@code EXPIRED} statuses, a caller-supplied
     * {@code decisionSequence}/{@code decisionRequestId} (the idempotency key) or
     * {@code policyException = true}, none of which the MVP-1 constructor above can
     * express. Callers are responsible for the {@code decision_sequence} value being the
     * real next sequence for this recommendation (typically
     * {@code MAX(decision_sequence) + 1} under the recommendation row's lock) and for
     * every {@code ck_sp_dec_mvp2_shape} shape rule already having been checked --
     * this factory does not re-validate them, matching how the MVP-1 constructor also
     * trusts its caller.
     */
    public static SpRebalanceDecision createMvp2Decision(
            SpRebalanceRecommendation recommendation,
            int decisionSequence,
            DecisionStatus decisionStatus,
            Integer selectedQuantity,
            String reasonCode,
            String reason,
            String actorLabel,
            int recommendationVersion,
            String decisionRequestId,
            boolean policyException) {
        SpRebalanceDecision decision = new SpRebalanceDecision();
        decision.recommendation = recommendation;
        decision.decisionStatus = decisionStatus;
        decision.selectedQuantity = selectedQuantity;
        decision.reasonCode = reasonCode;
        decision.reason = reason;
        decision.actorLabel = actorLabel;
        decision.decisionSequence = decisionSequence;
        decision.decisionContractVersion = "MVP-2";
        decision.recommendationVersion = recommendationVersion;
        decision.decisionRequestId = decisionRequestId;
        decision.policyExceptionFlag = policyException ? "Y" : "N";
        return decision;
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

    public int getDecisionSequence() {
        return decisionSequence;
    }

    public String getDecisionContractVersion() {
        return decisionContractVersion;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public int getRecommendationVersion() {
        return recommendationVersion;
    }

    public String getDecisionRequestId() {
        return decisionRequestId;
    }

    public boolean isPolicyException() {
        return "Y".equals(policyExceptionFlag);
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
