package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.demand.ManualQuantityEvaluation;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecision;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The side-effect-free `MANUAL` quantity-test use case, per
 * {@code knowledge/business-rules.md} section 10. {@code @Transactional} only so the same
 * recommendation-then-donor-snapshot {@code PESSIMISTIC_WRITE} lock order the approval
 * transaction uses can be taken for a consistent read -- nothing is ever saved or flushed, and
 * the transaction always ends by simply completing (there is nothing to commit or roll back).
 * <p>
 * Shares {@link CurrentApprovalBasisLoader} with {@link ApprovalTransactionExecutor} for the
 * version check, the donor lock, and every query/cross-validation below it, so a quantity test
 * and a real approval can never silently disagree about what is currently feasible.
 */
@Service
public class ManualQuantityTestExecutor {

    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpRebalanceDecisionRepository decisionRepository;
    private final CurrentApprovalBasisLoader basisLoader;
    private final PersistenceErrorTranslator errorTranslator;

    public ManualQuantityTestExecutor(
            SpRebalanceRecommendationRepository recommendationRepository,
            SpRebalanceDecisionRepository decisionRepository,
            CurrentApprovalBasisLoader basisLoader,
            PersistenceErrorTranslator errorTranslator) {
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.basisLoader = basisLoader;
        this.errorTranslator = errorTranslator;
    }

    @Transactional
    public ManualQuantityTestResult test(ManualQuantityTestCommand command) {
        try {
            return doTest(command);
        } catch (DataAccessException e) {
            throw errorTranslator.translate(e);
        }
    }

    private ManualQuantityTestResult doTest(ManualQuantityTestCommand command) {
        SpRebalanceRecommendation recommendation = recommendationRepository.lockById(command.recommendationId())
                .orElseThrow(() -> new ApprovalTransactionException(ApprovalErrorCode.RECOMMENDATION_NOT_FOUND,
                        "No recommendation found for id " + command.recommendationId() + "."));

        SpRebalanceDecision latest = decisionRepository
                .findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc(recommendation.getRecommendationId())
                .orElse(null);
        DecisionStatus currentStatus = latest == null ? DecisionStatus.PENDING : latest.getDecisionStatus();
        if (currentStatus.isTerminalForFurtherDecision()) {
            throw new ApprovalTransactionException(ApprovalErrorCode.DECISION_ALREADY_TERMINAL,
                    "Decision is already terminal (" + currentStatus + ").");
        }

        SpInventoryMetric receiverMetric = recommendation.getReceiverMetric();
        SpAnalysisRun analysisRun = receiverMetric.getAnalysisRun();
        basisLoader.validateCurrent(recommendation, analysisRun, command.analysisRunId(),
                command.inputSnapshotVersion(), command.ruleVersion(), command.candidateVersion());

        LoadedApprovalBasis loaded = basisLoader.load(recommendation, analysisRun, command.inputSnapshotVersion());

        ManualQuantityEvaluation evaluation;
        try {
            evaluation = ManualQuantityEvaluation.calculate(
                    loaded.skuId(), loaded.receiverStoreId(), loaded.receiverOwnerCode(),
                    loaded.donorStoreId(), loaded.donorOwnerCode(), loaded.route(), loaded.analysisDate(),
                    loaded.receiverProjection(), loaded.receiverBaseRate(),
                    loaded.receiverTargetCoverageDays(), loaded.receiverDisplayMinimum(), loaded.receiverMaximumCapacity(),
                    loaded.donorProjection(), loaded.donorBaseRate(), loaded.donorHighRate(),
                    loaded.donorRetainedDays(), loaded.donorDisplayMinimum(), loaded.donorSafetyStock(),
                    loaded.receiverHasConfirmedInbound(), loaded.pendingTransferConflict(),
                    command.requestedQuantity());
        } catch (IllegalArgumentException e) {
            throw new ApprovalTransactionException(
                    ApprovalErrorCode.STALE_RECOMMENDATION, "Recommendation is no longer current.", e);
        }

        return new ManualQuantityTestResult(
                command.recommendationId(), command.analysisRunId(), command.inputSnapshotVersion(),
                command.ruleVersion(), command.candidateVersion(), evaluation.requestedQuantity(),
                evaluation.feasible(), evaluation.reasonRequired(), evaluation.recommendedBaseQuantity(),
                evaluation.maximumFeasibleQuantity(), evaluation.suggestedQuantity(),
                evaluation.violations(), evaluation.candidateRejectionReasons(),
                evaluation.routeMinimumQuantity(), evaluation.packageMultiple(), evaluation.routeMaximumQuantity(),
                evaluation.donorTransferableQuantity(), evaluation.receiverCapacityRemaining(),
                evaluation.projection(), true);
    }
}
