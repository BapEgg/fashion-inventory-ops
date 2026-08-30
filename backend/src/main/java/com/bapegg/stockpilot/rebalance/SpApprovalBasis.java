package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * The approval-time basis snapshot for one {@link SpRebalanceDecision}, per
 * {@code knowledge/business-rules.md} section 10 and {@code V10}/{@code V11}'s
 * {@code sp_approval_basis}. 1:1 with the decision it backs
 * ({@code uq_sp_basis_decision}); {@code candidateEligibleFlag} is always {@code 'Y'}
 * ({@code V11}'s {@code ck_sp_basis_eligible}) since an ineligible candidate is
 * rejected as {@code STALE_RECOMMENDATION} before ever reaching a row here -- the
 * pure-Java {@code com.bapegg.stockpilot.demand.ApprovalRequestValidation} owns that
 * decision, this entity only persists its outcome. {@code ApprovalTransactionExecutor}
 * writes one row per {@code APPROVED} decision (never for {@code HELD}/{@code REJECTED});
 * {@code Mvp2DecisionHistoryQueryService} reads it back through
 * {@code GET /api/rebalancing-decisions/{recommendationId}}.
 */
@Entity
@Table(name = "sp_approval_basis")
public class SpApprovalBasis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_basis_id")
    private Long approvalBasisId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, unique = true)
    private SpRebalanceDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private SpAnalysisRun analysisRun;

    @Column(name = "input_snapshot_version", nullable = false, length = 64)
    private String inputSnapshotVersion;

    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion;

    @Column(name = "candidate_version", nullable = false)
    private int candidateVersion;

    @Column(name = "candidate_eligible_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String candidateEligibleFlag;

    @Column(name = "recommended_base_quantity", nullable = false)
    private long recommendedBaseQuantity;

    @Column(name = "donor_transferable_quantity", nullable = false)
    private long donorTransferableQuantity;

    @Column(name = "route_minimum_quantity", nullable = false)
    private int routeMinimumQuantity;

    @Column(name = "package_multiple", nullable = false)
    private int packageMultiple;

    @Column(name = "route_maximum_quantity", nullable = false)
    private int routeMaximumQuantity;

    @Column(name = "receiver_capacity_remaining", nullable = false)
    private long receiverCapacityRemaining;

    @Column(name = "basis_contract_version", nullable = false, length = 32)
    private String basisContractVersion;

    @Column(name = "receiver_projected_before_demand", nullable = false)
    private long receiverProjectedBeforeDemand;

    @Column(name = "donor_projected_at_dispatch", nullable = false)
    private long donorProjectedAtDispatch;

    @Column(name = "already_approved_draft_quantity", nullable = false)
    private long alreadyApprovedDraftQuantity;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpApprovalBasis() {
    }

    public SpApprovalBasis(
            SpRebalanceDecision decision,
            SpAnalysisRun analysisRun,
            String inputSnapshotVersion,
            String ruleVersion,
            int candidateVersion,
            long recommendedBaseQuantity,
            long donorTransferableQuantity,
            int routeMinimumQuantity,
            int packageMultiple,
            int routeMaximumQuantity,
            long receiverCapacityRemaining,
            long receiverProjectedBeforeDemand,
            long donorProjectedAtDispatch,
            long alreadyApprovedDraftQuantity) {
        this.decision = decision;
        this.analysisRun = analysisRun;
        this.inputSnapshotVersion = inputSnapshotVersion;
        this.ruleVersion = ruleVersion;
        this.candidateVersion = candidateVersion;
        this.candidateEligibleFlag = "Y";
        this.recommendedBaseQuantity = recommendedBaseQuantity;
        this.donorTransferableQuantity = donorTransferableQuantity;
        this.routeMinimumQuantity = routeMinimumQuantity;
        this.packageMultiple = packageMultiple;
        this.routeMaximumQuantity = routeMaximumQuantity;
        this.receiverCapacityRemaining = receiverCapacityRemaining;
        this.basisContractVersion = "MVP-2";
        this.receiverProjectedBeforeDemand = receiverProjectedBeforeDemand;
        this.donorProjectedAtDispatch = donorProjectedAtDispatch;
        this.alreadyApprovedDraftQuantity = alreadyApprovedDraftQuantity;
    }

    public Long getApprovalBasisId() {
        return approvalBasisId;
    }

    public SpRebalanceDecision getDecision() {
        return decision;
    }

    public SpAnalysisRun getAnalysisRun() {
        return analysisRun;
    }

    public String getInputSnapshotVersion() {
        return inputSnapshotVersion;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public int getCandidateVersion() {
        return candidateVersion;
    }

    public boolean isCandidateEligible() {
        return "Y".equals(candidateEligibleFlag);
    }

    public long getRecommendedBaseQuantity() {
        return recommendedBaseQuantity;
    }

    public long getDonorTransferableQuantity() {
        return donorTransferableQuantity;
    }

    public int getRouteMinimumQuantity() {
        return routeMinimumQuantity;
    }

    public int getPackageMultiple() {
        return packageMultiple;
    }

    public int getRouteMaximumQuantity() {
        return routeMaximumQuantity;
    }

    public long getReceiverCapacityRemaining() {
        return receiverCapacityRemaining;
    }

    public String getBasisContractVersion() {
        return basisContractVersion;
    }

    public long getReceiverProjectedBeforeDemand() {
        return receiverProjectedBeforeDemand;
    }

    public long getDonorProjectedAtDispatch() {
        return donorProjectedAtDispatch;
    }

    public long getAlreadyApprovedDraftQuantity() {
        return alreadyApprovedDraftQuantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
