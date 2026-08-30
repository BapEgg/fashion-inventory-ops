package com.bapegg.stockpilot.api.error;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One stable application error code's HTTP/ProblemDetail presentation, per {@code V10}/{@code V11}'s
 * {@code sp_error_catalog}. Read-only: rows are seeded by Migration, and nothing in this codebase
 * writes a new one through this entity.
 */
@Entity
@Table(name = "sp_error_catalog")
public class SpErrorCatalog {

    @Id
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "retryable_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String retryableFlag;

    @Column(name = "message_ko", nullable = false, length = 500)
    private String messageKo;

    @Column(name = "title_ko", nullable = false, length = 200)
    private String titleKo;

    @Column(name = "default_detail_ko", nullable = false, length = 500)
    private String defaultDetailKo;

    @Column(name = "active_flag", nullable = false, columnDefinition = "CHAR(1 CHAR)")
    private String activeFlag;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected SpErrorCatalog() {
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return "Y".equals(retryableFlag);
    }

    public String getMessageKo() {
        return messageKo;
    }

    public String getTitleKo() {
        return titleKo;
    }

    public String getDefaultDetailKo() {
        return defaultDetailKo;
    }

    public boolean isActive() {
        return "Y".equals(activeFlag);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
