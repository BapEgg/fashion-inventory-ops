package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One Oracle constraint-name to application error-code mapping, per {@code V10}'s
 * {@code sp_error_constraint_map}. Rows are seeded by {@code V10}/{@code V11}; nothing
 * in this codebase writes a new one, so there is no public constructor -- this entity
 * is a read-only lookup for {@link PersistenceErrorTranslator}.
 */
@Entity
@Table(name = "sp_error_constraint_map")
public class SpErrorConstraintMap {

    @Id
    @Column(name = "constraint_name", length = 128)
    private String constraintName;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SpErrorConstraintMap() {
    }

    public String getConstraintName() {
        return constraintName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
