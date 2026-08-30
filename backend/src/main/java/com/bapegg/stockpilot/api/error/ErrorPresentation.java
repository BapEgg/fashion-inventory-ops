package com.bapegg.stockpilot.api.error;

/**
 * The resolved HTTP presentation for one error code, either read straight from
 * {@code sp_error_catalog} or one of {@link ErrorCatalogService}'s two Java fallbacks (used only
 * when the catalog itself cannot be trusted: a connectivity failure, or a missing/inactive row).
 * <p>
 * {@code code} is the *effective* code -- the one this presentation's status/title/detail/
 * retryable actually describe -- which is deliberately not always the code the caller originally
 * asked to resolve: a missing/inactive row's presentation describes
 * {@link ApiErrorCode#INTERNAL_SERVER_ERROR}, and a failed lookup's presentation describes
 * {@link ApiErrorCode#PERSISTENCE_UNAVAILABLE}, so a caller that put the *original* code on the
 * response body/type would produce a response whose HTTP status and stable code disagree.
 */
public record ErrorPresentation(String code, int httpStatus, String title, String detail, boolean retryable) {

    static ErrorPresentation from(SpErrorCatalog catalog) {
        return new ErrorPresentation(
                catalog.getErrorCode(), catalog.getHttpStatus(), catalog.getTitleKo(), catalog.getDefaultDetailKo(), catalog.isRetryable());
    }

    /** Missing or inactive catalog row: never invented per-code text, just this one fixed fallback. */
    static ErrorPresentation internalFallback() {
        return new ErrorPresentation(
                ApiErrorCode.INTERNAL_SERVER_ERROR, 500, "내부 오류", "예기치 않은 오류가 발생했습니다.", false);
    }

    /** The catalog lookup itself failed (persistence unavailable): the same fixed fallback every time. */
    static ErrorPresentation persistenceUnavailableFallback() {
        return new ErrorPresentation(ApiErrorCode.PERSISTENCE_UNAVAILABLE, 503,
                "저장소 일시 불가", "저장소에 일시적으로 접근할 수 없습니다. 잠시 후 다시 시도하세요.", true);
    }
}
