package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.api.error.ApiFieldError;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import com.bapegg.stockpilot.approval.ApprovalTransactionCommand;
import com.bapegg.stockpilot.approval.ApprovalTransactionException;
import com.bapegg.stockpilot.approval.ApprovalTransactionFacade;
import com.bapegg.stockpilot.approval.ApprovalTransactionResult;
import com.bapegg.stockpilot.approval.PersistenceErrorTranslator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@Validated
public class RebalanceDecisionController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String GENERIC_FIELD_ERROR_MESSAGE = "요청 값이 유효하지 않습니다.";

    private final RebalanceDecisionService rebalanceDecisionService;
    private final ApprovalTransactionFacade approvalTransactionFacade;
    private final Mvp2DecisionHistoryQueryService decisionHistoryQueryService;
    private final PersistenceErrorTranslator errorTranslator;

    public RebalanceDecisionController(
            RebalanceDecisionService rebalanceDecisionService,
            ApprovalTransactionFacade approvalTransactionFacade,
            Mvp2DecisionHistoryQueryService decisionHistoryQueryService,
            PersistenceErrorTranslator errorTranslator) {
        this.rebalanceDecisionService = rebalanceDecisionService;
        this.approvalTransactionFacade = approvalTransactionFacade;
        this.decisionHistoryQueryService = decisionHistoryQueryService;
        this.errorTranslator = errorTranslator;
    }

    /**
     * Branches on the additive MVP-2 signals, per current-task.md section 1: the version tuple's
     * four fields, {@code policyException}, {@code reasonCode} and the {@code Idempotency-Key}
     * header. None of them present at all keeps the exact legacy MVP-1 request/response shape,
     * calculation and 201 -- no {@code Location} header, no new response wrapper. Any one of them
     * present requires all four tuple fields and exactly one {@code Idempotency-Key} header
     * together; a partial combination is rejected outright rather than filling gaps from the
     * recommendation. The complete MVP-2 shape is routed to
     * {@link ApprovalTransactionCommand}'s own canonical constructor, which owns every
     * status/quantity/policy-exception cross-field rule -- this controller only builds the command
     * and calls the facade.
     * <p>
     * Per the Codex review's P2 findings: the legacy branch validates its own
     * {@code selectedQuantity}/{@code reason} field shape here, before {@link
     * RebalanceDecisionService} ever touches a repository, so an invalid body is always reported
     * as {@code VALIDATION_ERROR} rather than depending on database state
     * ({@code RECOMMENDATION_NOT_FOUND}/{@code DECISION_ALREADY_TERMINAL}/non-MVP-1). The legacy
     * writer also has no lock, so two concurrent legacy requests for the same recommendation can
     * both pass its existence check and race to insert -- the loser's
     * {@code UQ_SP_DEC_REC_SEQ} violation is translated here via the same
     * {@link PersistenceErrorTranslator} the accepted approval transaction already uses, into the
     * catalog's {@code DECISION_CONFLICT}, never a raw {@code INTERNAL_SERVER_ERROR}.
     */
    @PostMapping("/api/rebalancing-decisions")
    public ResponseEntity<Object> decide(
            @Valid @RequestBody RebalanceDecisionRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) List<String> idempotencyKeyHeaders) {
        boolean tupleAnyPresent = request.analysisRunId() != null || request.inputSnapshotVersion() != null
                || request.ruleVersion() != null || request.candidateVersion() != null;
        boolean headerPresent = idempotencyKeyHeaders != null && !idempotencyKeyHeaders.isEmpty();
        boolean mvp2BodyFieldPresent = request.policyException() != null || request.reasonCode() != null;

        if (!tupleAnyPresent && !headerPresent && !mvp2BodyFieldPresent) {
            validateLegacyShape(request);
            RebalanceDecisionResponse response;
            try {
                response = rebalanceDecisionService.decide(
                        request.recommendationId(), request.decisionStatus(), request.selectedQuantity(),
                        request.reason(), request.actorLabel());
            } catch (DataIntegrityViolationException e) {
                throw errorTranslator.translate(e);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        boolean tupleAllPresent = request.analysisRunId() != null && request.inputSnapshotVersion() != null
                && request.ruleVersion() != null && request.candidateVersion() != null;
        if (!tupleAllPresent || !headerPresent || idempotencyKeyHeaders.size() != 1
                || idempotencyKeyHeaders.get(0).contains(",")) {
            throw new ApprovalTransactionException(ApprovalErrorCode.INVALID_DECISION_REQUEST,
                    "analysisRunId, inputSnapshotVersion, ruleVersion, candidateVersion and exactly one "
                            + "Idempotency-Key header must all be present together.");
        }

        ApprovalTransactionCommand command = new ApprovalTransactionCommand(
                request.recommendationId(), request.analysisRunId(), request.inputSnapshotVersion(),
                request.ruleVersion(), request.candidateVersion(), request.decisionStatus(),
                request.selectedQuantity(), Boolean.TRUE.equals(request.policyException()),
                request.reasonCode(), request.reason(), request.actorLabel());
        ApprovalTransactionResult result = approvalTransactionFacade.execute(command, idempotencyKeyHeaders.get(0));

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(locationOf(request.recommendationId()))
                .body(Mvp2RebalanceDecisionResponse.from(request.recommendationId(), result));
    }

    /**
     * The legacy-only {@code selectedQuantity}/{@code reason} required-ness Bean Validation can no
     * longer express statically (MVP-2's {@code HELD}/{@code REJECTED} legitimately need
     * {@code selectedQuantity=null}). Runs before {@link RebalanceDecisionService} touches any
     * repository, so an invalid legacy body is always {@code VALIDATION_ERROR}, never a
     * database-state-dependent code.
     */
    private void validateLegacyShape(RebalanceDecisionRequest request) {
        List<ApiFieldError> fieldErrors = new ArrayList<>();
        if (request.selectedQuantity() == null) {
            fieldErrors.add(new ApiFieldError("selectedQuantity", "REQUIRED", GENERIC_FIELD_ERROR_MESSAGE));
        } else if (request.selectedQuantity() < 1) {
            fieldErrors.add(new ApiFieldError("selectedQuantity", "FORMAT", GENERIC_FIELD_ERROR_MESSAGE));
        }
        if (request.reason() == null || request.reason().isBlank()) {
            fieldErrors.add(new ApiFieldError("reason", "REQUIRED", GENERIC_FIELD_ERROR_MESSAGE));
        }
        if (!fieldErrors.isEmpty()) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR,
                    "Legacy decision request is missing required fields.", fieldErrors);
        }
    }

    @GetMapping("/api/rebalancing-decisions/{recommendationId}")
    public Mvp2DecisionHistoryResponse history(@PathVariable @Positive Long recommendationId) {
        return decisionHistoryQueryService.getHistory(recommendationId);
    }

    private static URI locationOf(Long recommendationId) {
        return URI.create("/api/rebalancing-decisions/" + recommendationId);
    }
}
