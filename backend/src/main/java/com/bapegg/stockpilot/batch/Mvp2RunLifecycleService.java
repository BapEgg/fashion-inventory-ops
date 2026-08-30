package com.bapegg.stockpilot.batch;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * Non-{@code @Transactional} facade over {@link Mvp2RunLifecycleTransactions}' three separate
 * {@code REQUIRES_NEW} transactions, per current-task.md section 2. Deliberately calls the
 * injected {@link Mvp2RunLifecycleTransactions} bean rather than itself, so
 * {@code @Transactional}'s proxy actually applies to {@link #claim}'s two steps -- a
 * self-invocation would silently run {@code resolveCreateRace} inside the already-failed
 * transaction {@link #claim} left behind instead of a fresh one.
 */
@Service
public class Mvp2RunLifecycleService {

    private final Mvp2RunLifecycleTransactions transactions;

    public Mvp2RunLifecycleService(Mvp2RunLifecycleTransactions transactions) {
        this.transactions = transactions;
    }

    public Mvp2RunClaim claim(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {
        validateNaturalKey(analysisDate, inputSnapshotVersion, ruleVersion);
        try {
            return transactions.claim(analysisDate, inputSnapshotVersion, ruleVersion);
        } catch (DataIntegrityViolationException insertFailure) {
            try {
                return transactions.resolveCreateRace(analysisDate, inputSnapshotVersion, ruleVersion);
            } catch (NoSuchElementException noRowFound) {
                throw insertFailure;
            }
        }
    }

    public void markFailed(Long runId) {
        transactions.markFailed(runId);
    }

    private static void validateNaturalKey(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {
        if (analysisDate == null) {
            throw new IllegalArgumentException("analysisDate must not be null.");
        }
        if (inputSnapshotVersion == null || inputSnapshotVersion.isBlank()) {
            throw new IllegalArgumentException("inputSnapshotVersion must not be null or blank.");
        }
        if (ruleVersion == null || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("ruleVersion must not be null or blank.");
        }
    }
}
