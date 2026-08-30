package com.bapegg.stockpilot.api.error;

import com.bapegg.stockpilot.analysis.AnalysisController;
import com.bapegg.stockpilot.analysis.AnalysisLaunchFailureClassifier;
import com.bapegg.stockpilot.analysis.InventoryExceptionController;
import com.bapegg.stockpilot.approval.ApprovalTransactionException;
import com.bapegg.stockpilot.rebalance.RebalanceDecisionController;
import com.bapegg.stockpilot.rebalance.RebalanceSimulationController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The only ProblemDetail (RFC 9457) boundary for {@link AnalysisController},
 * {@link InventoryExceptionController}, {@link RebalanceSimulationController} and
 * {@link RebalanceDecisionController}, per current-task.md section 5 (the approval/decision REST
 * slice). Scoped to exactly these controllers via {@code assignableTypes} -- other controllers
 * keep their own existing error handling untouched. Every response carries the
 * *effective* catalog presentation's status/title/detail/retryable/code -- {@link ErrorPresentation#code()},
 * not necessarily the code a caller originally asked to resolve, since a missing/inactive row or a
 * failed lookup resolves to a different presentation entirely and the response must describe that
 * one consistently (see {@link ErrorPresentation}) -- a {@code urn:stockpilot:error:<code>} type
 * using that same effective code, the request URI as {@code instance}, a UUID {@code requestId}
 * (also set on the response header by {@link RequestIdFilter}) and a UTC {@code timestamp}. Only
 * validation responses add a {@code fieldErrors} list sorted by {@code (field, code)}; no response
 * ever includes a rejected value or a raw SQL/constraint/stack message.
 */
@RestControllerAdvice(assignableTypes = {
        AnalysisController.class, InventoryExceptionController.class, RebalanceSimulationController.class,
        RebalanceDecisionController.class})
public class AnalysisApiExceptionHandler {

    private static final String GENERIC_FIELD_ERROR_MESSAGE = "요청 값이 유효하지 않습니다.";

    private final ErrorCatalogService errorCatalogService;
    private final AnalysisLaunchFailureClassifier failureClassifier;

    public AnalysisApiExceptionHandler(ErrorCatalogService errorCatalogService, AnalysisLaunchFailureClassifier failureClassifier) {
        this.errorCatalogService = errorCatalogService;
        this.failureClassifier = failureClassifier;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException e, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = e.fieldErrors().isEmpty() ? null : sorted(e.fieldErrors());
        return respond(e.code(), request, fieldErrors);
    }

    /**
     * {@link ApprovalTransactionException} is the {@code MANUAL} quantity-test executor's and the
     * approval transaction's (POST's MVP-2 branch, including {@code RebalanceDecisionController}'s
     * own tuple/header-cardinality check ahead of it) own code-carrying failure -- see
     * {@code ApprovalErrorCode} for the stable codes it can carry, all already seeded into
     * {@code sp_error_catalog} by V10/V11. No field errors: this contract has none. The decision-
     * history GET ({@code Mvp2DecisionHistoryQueryService}) and the tuple-less legacy branch's
     * domain failures use the plain {@link ApiException} above; a legacy save-time constraint
     * failure is translated to {@link ApprovalTransactionException} after rollback. Both handlers
     * resolve the same {@code ApprovalErrorCode} values through the same catalog either way.
     */
    @ExceptionHandler(ApprovalTransactionException.class)
    public ResponseEntity<ProblemDetail> handleApprovalTransactionException(
            ApprovalTransactionException e, HttpServletRequest request) {
        return respond(e.code(), request, null);
    }

    /**
     * A {@link DataAccessException} raised outside a Job launch entirely -- e.g.
     * {@code AnalysisRunQueryService.findById} (GET) or {@code Mvp2AnalysisApplicationService}'s
     * pre-/post-launch domain-run reads (POST) -- would otherwise bypass this controller's
     * catalog-backed ProblemDetail boundary and fall through to Spring's own default error
     * response. Classified through the same single boundary the launch paths use, per the P1
     * finding.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        ApiException classified = failureClassifier.classifyDataAccess(e);
        return respond(classified.code(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = sorted(e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), categorize(fe.getCode()), safeMessage(fe)))
                .toList());
        return respond(ApiErrorCode.VALIDATION_ERROR, request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = sorted(e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(resolvable -> new ApiFieldError(
                                parameterName(result), categorize(lastCode(resolvable)), GENERIC_FIELD_ERROR_MESSAGE)))
                .toList());
        return respond(ApiErrorCode.VALIDATION_ERROR, request, fieldErrors);
    }

    /**
     * A {@code @Validated}-class {@code @PathVariable}/{@code @RequestParam} constraint (e.g. the
     * GET endpoint's {@code @Positive analysisRunId}) surfaces here as a raw
     * {@link ConstraintViolationException} on this Spring Framework version, rather than the newer
     * {@code HandlerMethodValidationException} -- both are handled, since which one a given Spring
     * version throws is an implementation detail this contract must not depend on.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = sorted(e.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        lastPathSegment(violation.getPropertyPath().toString()),
                        categorize(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                        GENERIC_FIELD_ERROR_MESSAGE))
                .toList());
        return respond(ApiErrorCode.VALIDATION_ERROR, request, fieldErrors);
    }

    /** A non-numeric {@code @PathVariable Long} (e.g. GET's {@code analysisRunId}) fails type conversion, not a constraint. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = List.of(new ApiFieldError(e.getName(), "FORMAT", GENERIC_FIELD_ERROR_MESSAGE));
        return respond(ApiErrorCode.VALIDATION_ERROR, request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        return respond(ApiErrorCode.VALIDATION_ERROR, request, List.of());
    }

    /**
     * Normalizes the existing MVP-1 {@link ResponseStatusException} throws (still used by
     * {@code AnalysisRunService}) into the same catalog-backed presentation, per current-task.md:
     * "기존 ResponseStatusException은 400→validation, 409→running, 나머지→internal로 정규화한다."
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        String code = switch (e.getStatusCode().value()) {
            case 400 -> ApiErrorCode.VALIDATION_ERROR;
            case 409 -> ApiErrorCode.ANALYSIS_ALREADY_RUNNING;
            default -> ApiErrorCode.INTERNAL_SERVER_ERROR;
        };
        return respond(code, request, null);
    }

    private ResponseEntity<ProblemDetail> respond(String requestedCode, HttpServletRequest request, List<ApiFieldError> fieldErrors) {
        ErrorPresentation presentation = errorCatalogService.resolve(requestedCode);
        String effectiveCode = presentation.code();
        String requestId = requestId(request);

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(presentation.httpStatus()), presentation.detail());
        body.setTitle(presentation.title());
        body.setType(URI.create("urn:stockpilot:error:" + effectiveCode));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", effectiveCode);
        body.setProperty("retryable", presentation.retryable());
        body.setProperty("requestId", requestId);
        body.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        // Per the P2 finding: fieldErrors is a validation-only contract. If catalog resolution
        // changed the effective presentation away from VALIDATION_ERROR (a missing/inactive row or
        // a failed lookup falling back to persistence/internal), a validation-shaped fieldErrors
        // list must never ride along on a response that no longer describes a validation failure.
        if (fieldErrors != null && ApiErrorCode.VALIDATION_ERROR.equals(effectiveCode)) {
            body.setProperty("fieldErrors", fieldErrors);
        }

        return ResponseEntity.status(presentation.httpStatus())
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .body(body);
    }

    private static List<ApiFieldError> sorted(List<ApiFieldError> fieldErrors) {
        return fieldErrors.stream()
                .sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::code))
                .toList();
    }

    private static String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }

    private static String lastPathSegment(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }

    private static String parameterName(org.springframework.validation.method.ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
    }

    /** The last dot-segment of a resolvable's most specific message code is the bare constraint annotation name. */
    private static String lastCode(MessageSourceResolvable resolvable) {
        String[] codes = resolvable.getCodes();
        if (codes == null || codes.length == 0) {
            return null;
        }
        String last = codes[codes.length - 1];
        int dot = last.lastIndexOf('.');
        return dot >= 0 ? last.substring(dot + 1) : last;
    }

    private static String categorize(String constraintAnnotationName) {
        if (constraintAnnotationName == null) {
            return "FORMAT";
        }
        return switch (constraintAnnotationName) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size" -> "SIZE";
            case "Null" -> "FORBIDDEN";
            default -> "FORMAT";
        };
    }

    private static String safeMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : GENERIC_FIELD_ERROR_MESSAGE;
    }
}
