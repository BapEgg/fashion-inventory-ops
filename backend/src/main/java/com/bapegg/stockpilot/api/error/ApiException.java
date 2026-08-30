package com.bapegg.stockpilot.api.error;

import java.util.List;

/**
 * A code-only application failure for any REST use case wired to {@code sp_error_catalog}. Never
 * carries the HTTP status, title or user-facing detail text itself -- those are always resolved
 * from the catalog (or its Java fallback) at handling time, so the same code always presents the
 * same way regardless of where it was thrown from. May optionally carry {@link ApiFieldError}s for
 * a validation-shaped failure raised outside Bean Validation itself (e.g. a service-layer check on
 * a field Bean Validation cannot express) -- empty for every other kind of failure.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final List<ApiFieldError> fieldErrors;

    public ApiException(String code, String diagnosticMessage) {
        this(code, diagnosticMessage, null, List.of());
    }

    public ApiException(String code, String diagnosticMessage, Throwable cause) {
        this(code, diagnosticMessage, cause, List.of());
    }

    public ApiException(String code, String diagnosticMessage, List<ApiFieldError> fieldErrors) {
        this(code, diagnosticMessage, null, fieldErrors);
    }

    private ApiException(String code, String diagnosticMessage, Throwable cause, List<ApiFieldError> fieldErrors) {
        super(diagnosticMessage, cause);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public String code() {
        return code;
    }

    public List<ApiFieldError> fieldErrors() {
        return fieldErrors;
    }
}
