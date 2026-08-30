package com.bapegg.stockpilot.api.error;

/**
 * One field-level validation error, per current-task.md section 3: {@code field}/{@code code}/
 * {@code message}, where {@code code} is one of {@code REQUIRED}/{@code SIZE}/{@code FORMAT}/
 * {@code FORBIDDEN}. Never carries the rejected value.
 */
public record ApiFieldError(String field, String code, String message) {
}
