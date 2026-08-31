package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.demand.ApprovalBasisRecalculation;
import com.bapegg.stockpilot.demand.ApprovalRequest;
import com.bapegg.stockpilot.demand.ApprovalRequestValidation;
import com.bapegg.stockpilot.demand.RecommendationBasis;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import com.bapegg.stockpilot.rebalance.SpApprovalBasis;
import com.bapegg.stockpilot.rebalance.SpApprovalBasisRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecision;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpTransferDraft;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The {@code @Transactional} write path of the approval command, per
 * {@code knowledge/business-rules.md} section 10. Called only by
 * {@link ApprovalTransactionFacade} after the facade has already confirmed (via
 * {@link ApprovalTransactionReader}, in its own separate transaction) that no decision
 * with this idempotency key exists yet.
 * <p>
 * Lock order is always recommendation, then (only for {@code APPROVED}) the donor's
 * inventory snapshot; the reverse order is never taken. A 3-second lock-wait
 * ({@code jakarta.persistence.lock.timeout} on both lock queries) is a demo
 * {@code ASSUMPTION} technical policy, not a real operational SLA. Version/currency
 * validation and the donor-snapshot-locked basis load are shared with the side-effect-free
 * {@code ManualQuantityTestExecutor} via {@link CurrentApprovalBasisLoader}.
 */
@Service
public class ApprovalTransactionExecutor {

    private static final String DRAFT_PAYLOAD_VERSION = "MVP-2-DRAFT-V1";

    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpRebalanceDecisionRepository decisionRepository;
    private final SpApprovalBasisRepository approvalBasisRepository;
    private final SpTransferDraftRepository transferDraftRepository;
    private final CurrentApprovalBasisLoader basisLoader;
    private final ApprovalTransactionReader reader;
    private final PersistenceErrorTranslator errorTranslator;

    public ApprovalTransactionExecutor(
            SpRebalanceRecommendationRepository recommendationRepository,
            SpRebalanceDecisionRepository decisionRepository,
            SpApprovalBasisRepository approvalBasisRepository,
            SpTransferDraftRepository transferDraftRepository,
            CurrentApprovalBasisLoader basisLoader,
            ApprovalTransactionReader reader,
            PersistenceErrorTranslator errorTranslator) {
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.approvalBasisRepository = approvalBasisRepository;
        this.transferDraftRepository = transferDraftRepository;
        this.basisLoader = basisLoader;
        this.reader = reader;
        this.errorTranslator = errorTranslator;
    }

    @Transactional
    public ApprovalTransactionResult execute(ApprovalTransactionCommand command, String idempotencyKey, String fingerprint) {
        try {
            return doExecute(command, idempotencyKey, fingerprint);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Never translate a constraint violation here: PersistenceErrorTranslator's
            // constraint-map lookup is itself a repository call, and this @Transactional
            // method's persistence context/session is not guaranteed usable for a further
            // query once a flush has already failed. Always propagate raw so the surrounding
            // @Transactional proxy finishes the rollback first; ApprovalTransactionFacade
            // (running after this transaction is fully closed) does the translation --
            // including the UQ_SP_DEC_REQUEST_ID winner-reread special case -- in its own,
            // separate, guaranteed-fresh transaction.
            throw e;
        } catch (DataAccessException e) {
            // Lock-timeout/connection-failure classification is pure exception-type/message
            // inspection with no DB round trip, so translating it here (before rollback) is
            // safe -- unlike the constraint-map lookup above.
            throw errorTranslator.translate(e);
        }
    }

    private ApprovalTransactionResult doExecute(ApprovalTransactionCommand command, String idempotencyKey, String fingerprint) {
        SpRebalanceRecommendation recommendation = recommendationRepository.lockById(command.recommendationId())
                .orElseThrow(() -> new ApprovalTransactionException(ApprovalErrorCode.RECOMMENDATION_NOT_FOUND,
                        "No recommendation found for id " + command.recommendationId() + "."));

        // A concurrent transaction on a DIFFERENT recommendation cannot be serialized by the
        // lock just taken above, so the key must be re-checked now that we hold this lock.
        Optional<SpRebalanceDecision> raceWinner = decisionRepository.findByDecisionRequestId(idempotencyKey);
        if (raceWinner.isPresent()) {
            return replayOrThrowConflict(raceWinner.get(), fingerprint);
        }

        SpRebalanceDecision latest = decisionRepository
                .findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc(recommendation.getRecommendationId())
                .orElse(null);
        DecisionStatus currentStatus = latest == null ? DecisionStatus.PENDING : latest.getDecisionStatus();
        validateTransition(currentStatus);
        int nextSequence = latest == null ? 1 : latest.getDecisionSequence() + 1;

        SpInventoryMetric receiverMetric = recommendation.getReceiverMetric();
        SpAnalysisRun analysisRun = receiverMetric.getAnalysisRun();
        basisLoader.validateCurrent(recommendation, analysisRun, command.analysisRunId(),
                command.inputSnapshotVersion(), command.ruleVersion(), command.candidateVersion());

        // Redesign spec section 4.7 fail-closed guard, checked once here -- after the recommendation
        // lock and version validation, before any status-specific write branch -- so a COMPARISON_ONLY
        // or already-REJECTED recommendation can never get a HELD/APPROVED/REJECTED decision, basis
        // or draft row written for it via a direct/out-of-band API call, even though the UI itself
        // never offers a decision action for such a candidate (section 8.5).
        if (recommendation.getCandidateStatus() != CandidateStatus.ELIGIBLE
                || recommendation.getRecommendationMode() != RecommendationMode.RECOMMENDED) {
            throw staleRecommendation();
        }

        if (command.status() != DecisionStatus.APPROVED) {
            SpRebalanceDecision decision = decisionRepository.save(SpRebalanceDecision.createMvp2Decision(
                    recommendation, nextSequence, command.status(), null,
                    command.reasonCode(), command.reason(), command.actorLabel(),
                    command.candidateVersion(), idempotencyKey, false));
            decisionRepository.flush();
            return new ApprovalTransactionResult(
                    decision.getDecisionId(), decision.getDecisionStatus(), decision.getDecisionSequence(), null, true);
        }

        LoadedApprovalBasis loaded = basisLoader.load(recommendation, analysisRun, command.inputSnapshotVersion());

        ApprovalBasisRecalculation recalc;
        try {
            recalc = ApprovalBasisRecalculation.calculate(
                    loaded.skuId(), loaded.receiverStoreId(), loaded.receiverOwnerCode(),
                    loaded.donorStoreId(), loaded.donorOwnerCode(), loaded.route(),
                    loaded.receiverProjection(), loaded.receiverBaseRate(),
                    loaded.receiverTargetCoverageDays(), loaded.receiverDisplayMinimum(), loaded.receiverMaximumCapacity(),
                    loaded.donorProjection(), loaded.donorHighRate(),
                    loaded.donorRetainedDays(), loaded.donorDisplayMinimum(), loaded.donorSafetyStock(),
                    loaded.receiverHasConfirmedInbound(), loaded.pendingTransferConflict());
        } catch (IllegalArgumentException e) {
            throw staleRecommendation();
        }

        RecommendationBasis basis = new RecommendationBasis(
                String.valueOf(command.analysisRunId()), command.inputSnapshotVersion(), command.ruleVersion(),
                command.candidateVersion(),
                recalc.eligible(), recalc.recommendedBaseQuantity(), recalc.donorTransferableQuantity(),
                recalc.routeMinimumQuantity(), recalc.packageMultiple(), recalc.routeMaximumQuantity(),
                recalc.receiverCapacityRemaining());

        ApprovalRequestValidation validation;
        try {
            ApprovalRequest request = new ApprovalRequest(
                    String.valueOf(command.analysisRunId()), command.inputSnapshotVersion(), command.ruleVersion(),
                    command.candidateVersion(), com.bapegg.stockpilot.demand.DecisionStatus.APPROVED,
                    command.selectedQuantity(), command.policyException(), command.reasonCode(), command.reason());
            validation = ApprovalRequestValidation.validate(request, basis);
        } catch (IllegalArgumentException e) {
            // A changed quantity or an explicit policy exception without a reason code/reason
            // is a caller-contract shape defect (per ApprovalRequestValidation.validate's own
            // contract), not a staleness outcome -- translate it the same way command-shape
            // violations are translated in ApprovalTransactionCommand's own constructor.
            throw new ApprovalTransactionException(ApprovalErrorCode.INVALID_DECISION_REQUEST, e.getMessage(), e);
        }
        if (validation.stale()) {
            throw staleRecommendation();
        }

        SpRebalanceDecision decision = decisionRepository.save(SpRebalanceDecision.createMvp2Decision(
                recommendation, nextSequence, DecisionStatus.APPROVED, command.selectedQuantity(),
                command.reasonCode(), command.reason(), command.actorLabel(),
                command.candidateVersion(), idempotencyKey, command.policyException()));

        approvalBasisRepository.save(new SpApprovalBasis(
                decision, analysisRun, command.inputSnapshotVersion(), command.ruleVersion(), command.candidateVersion(),
                recalc.recommendedBaseQuantity(), recalc.donorTransferableQuantity(),
                recalc.routeMinimumQuantity(), recalc.packageMultiple(), recalc.routeMaximumQuantity(),
                recalc.receiverCapacityRemaining(), recalc.receiverProjectedBeforeDemand(),
                recalc.donorProjectedAtDispatch(), recalc.alreadyApprovedDraftQuantity()));

        SpTransferDraft draft = transferDraftRepository.save(new SpTransferDraft(
                decision, loaded.donorStoreId(), loaded.receiverStoreId(), loaded.skuId(),
                command.selectedQuantity(), DRAFT_PAYLOAD_VERSION));

        // Flush inside this transaction so a constraint violation on any of the three inserts
        // above surfaces (and rolls everything back) before this method returns.
        decisionRepository.flush();

        return new ApprovalTransactionResult(
                decision.getDecisionId(), decision.getDecisionStatus(), decision.getDecisionSequence(),
                draft.getTransferDraftId(), true);
    }

    private ApprovalTransactionResult replayOrThrowConflict(SpRebalanceDecision existing, String incomingFingerprint) {
        // The race winner's own transaction has already committed by the time our lock query
        // above returned, so this decision's associations are safe to navigate here, inside our
        // own still-active transaction. Reuses the reader's reconstruction so both call sites
        // agree on exactly how an existing decision's original command is rebuilt.
        ApprovalTransactionReader.ExistingKeyLookup lookup = reader.toLookup(existing);
        if (!lookup.fingerprint().equals(incomingFingerprint)) {
            throw new ApprovalTransactionException(ApprovalErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "decisionRequestId already used for a different request payload.");
        }
        return lookup.replayResult();
    }

    private void validateTransition(DecisionStatus currentStatus) {
        if (currentStatus.isTerminalForFurtherDecision()) {
            throw new ApprovalTransactionException(ApprovalErrorCode.DECISION_ALREADY_TERMINAL,
                    "Decision is already terminal (" + currentStatus + ").");
        }
    }

    private ApprovalTransactionException staleRecommendation() {
        return new ApprovalTransactionException(
                ApprovalErrorCode.STALE_RECOMMENDATION, "Recommendation is no longer current.");
    }
}
