package com.bapegg.stockpilot.approval;

/**
 * A rollback-triggering application failure for the approval transaction, carrying one
 * of {@link ApprovalErrorCode}'s stable codes. Never carries a raw SQL/constraint/stack
 * message in its own message text -- {@link PersistenceErrorTranslator} is responsible
 * for stripping that before wrapping a persistence failure here.
 */
public class ApprovalTransactionException extends RuntimeException {

    private final String code;

    public ApprovalTransactionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ApprovalTransactionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
