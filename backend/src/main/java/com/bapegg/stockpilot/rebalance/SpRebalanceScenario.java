package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.demand.TransferScenarioType;
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
import java.time.ZoneId;

/**
 * One persisted automatic scenario for an eligible candidate, per {@code V6}'s
 * {@code sp_rebalance_scenario} and {@code data-model.md} section 6. `MANUAL` is a
 * side-effect-free API result and is never persisted here.
 * <p>
 * {@code inbound_included_flag} reflects confirmed inbound only (receiver
 * {@code inboundArrivingBeforeTransfer} or donor {@code inboundArrivingBeforeDispatch}), per
 * {@code data-model.md}'s Phase 3 mapping -- {@code APPROVED}/{@code IN_TRANSIT} open transfer
 * and active drafts still shift before/after available quantities but do not widen this flag's
 * meaning.
 * <p>
 * {@code expected_arrival_at} and {@code candidate_version} are deliberately not caller-supplied
 * parameters: the former is always {@code result.expectedArrivalDate()} combined with
 * {@code 00:00 Asia/Seoul} (per {@code data-model.md}'s Phase 3 mapping), and the latter is
 * always {@code recommendation.getCandidateVersion()} -- deriving both from the calculation
 * result and its parent recommendation is the only way this audit row cannot disagree with the
 * basis it was computed from.
 */
@Entity
@Table(name = "sp_rebalance_scenario")
public class SpRebalanceScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scenario_id")
    private Long scenarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private SpRebalanceRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 20)
    private TransferScenarioType scenarioType;

    @Column(name = "demand_rate", nullable = false, precision = 18, scale = 12)
    private BigDecimal demandRate;

    @Column(name = "scenario_quantity", nullable = false)
    private long scenarioQuantity;

    @Column(name = "package_multiple", nullable = false)
    private int packageMultiple;

    @Column(name = "receiver_before_available", nullable = false)
    private int receiverBeforeAvailable;

    @Column(name = "receiver_after_available", nullable = false)
    private int receiverAfterAvailable;

    @Column(name = "receiver_before_coverage", precision = 18, scale = 6)
    private BigDecimal receiverBeforeCoverage;

    @Column(name = "receiver_after_coverage", precision = 18, scale = 6)
    private BigDecimal receiverAfterCoverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_risk_code", nullable = false, length = 30)
    private InventoryExceptionType receiverRiskCode;

    @Column(name = "donor_before_available", nullable = false)
    private int donorBeforeAvailable;

    @Column(name = "donor_after_available", nullable = false)
    private int donorAfterAvailable;

    @Column(name = "donor_before_coverage", precision = 18, scale = 6)
    private BigDecimal donorBeforeCoverage;

    @Column(name = "donor_after_coverage", precision = 18, scale = 6)
    private BigDecimal donorAfterCoverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "donor_risk_code", nullable = false, length = 30)
    private InventoryExceptionType donorRiskCode;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "expected_arrival_at", nullable = false)
    private OffsetDateTime expectedArrivalAt;

    @Column(name = "inbound_included_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String inboundIncludedFlag;

    @Column(name = "warning_summary", length = 1000)
    private String warningSummary;

    @Column(name = "candidate_version", nullable = false)
    private int candidateVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpRebalanceScenario() {
    }

    private static final ZoneId ASSUMPTION_TIMEZONE = ZoneId.of("Asia/Seoul");

    public SpRebalanceScenario(
            SpRebalanceRecommendation recommendation,
            TransferScenarioResult result,
            int packageMultiple) {
        this.recommendation = recommendation;
        this.scenarioType = result.scenarioType();
        this.demandRate = result.demandRate().setScale(12, RoundingMode.HALF_UP);
        this.scenarioQuantity = result.scenarioQuantity();
        this.packageMultiple = packageMultiple;
        this.receiverBeforeAvailable = result.receiverBeforeAvailable();
        this.receiverAfterAvailable = result.receiverAfterAvailable();
        this.receiverBeforeCoverage = scaleCoverage(result.receiverBeforeCoverageDays());
        this.receiverAfterCoverage = scaleCoverage(result.receiverAfterCoverageDays());
        this.receiverRiskCode = result.receiverRiskCode();
        this.donorBeforeAvailable = result.donorBeforeAvailable();
        this.donorAfterAvailable = result.donorAfterAvailable();
        this.donorBeforeCoverage = scaleCoverage(result.donorBeforeCoverageDays());
        this.donorAfterCoverage = scaleCoverage(result.donorAfterCoverageDays());
        this.donorRiskCode = result.donorRiskCode();
        this.leadTimeDays = result.leadTimeDays();
        this.expectedArrivalAt = result.expectedArrivalDate().atStartOfDay(ASSUMPTION_TIMEZONE).toOffsetDateTime();
        boolean inboundIncluded = result.receiverInboundArrivingBeforeTransfer() > 0
                || result.donorInboundArrivingBeforeDispatch() > 0;
        this.inboundIncludedFlag = inboundIncluded ? "Y" : "N";
        this.warningSummary = result.warningSummary();
        this.candidateVersion = recommendation.getCandidateVersion();
    }

    private static BigDecimal scaleCoverage(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public SpRebalanceRecommendation getRecommendation() {
        return recommendation;
    }

    public TransferScenarioType getScenarioType() {
        return scenarioType;
    }

    public BigDecimal getDemandRate() {
        return demandRate;
    }

    public long getScenarioQuantity() {
        return scenarioQuantity;
    }

    public int getPackageMultiple() {
        return packageMultiple;
    }

    public int getReceiverBeforeAvailable() {
        return receiverBeforeAvailable;
    }

    public int getReceiverAfterAvailable() {
        return receiverAfterAvailable;
    }

    public BigDecimal getReceiverBeforeCoverage() {
        return receiverBeforeCoverage;
    }

    public BigDecimal getReceiverAfterCoverage() {
        return receiverAfterCoverage;
    }

    public InventoryExceptionType getReceiverRiskCode() {
        return receiverRiskCode;
    }

    public int getDonorBeforeAvailable() {
        return donorBeforeAvailable;
    }

    public int getDonorAfterAvailable() {
        return donorAfterAvailable;
    }

    public BigDecimal getDonorBeforeCoverage() {
        return donorBeforeCoverage;
    }

    public BigDecimal getDonorAfterCoverage() {
        return donorAfterCoverage;
    }

    public InventoryExceptionType getDonorRiskCode() {
        return donorRiskCode;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public OffsetDateTime getExpectedArrivalAt() {
        return expectedArrivalAt;
    }

    public boolean isInboundIncluded() {
        return "Y".equals(inboundIncludedFlag);
    }

    public String getWarningSummary() {
        return warningSummary;
    }

    public int getCandidateVersion() {
        return candidateVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
