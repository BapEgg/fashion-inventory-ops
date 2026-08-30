package com.bapegg.stockpilot.demand;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * One store-SKU day of raw fact within the 28-day MVP-2 observation window, per
 * {@code knowledge/business-rules.md} section 2. Immutable and independent of Spring/JPA.
 * Mirrors {@code SP_INVENTORY_SNAPSHOT}'s {@code snapshot_at} and {@code out_of_stock_flag}
 * columns directly rather than re-deriving them from the on-hand/reserved quantities, since
 * both are independent Oracle inputs that can diverge from the quantity math (a stale
 * reconciliation timestamp, or an explicit OOS flag for damaged/held stock that the ledger
 * still shows as on hand).
 * <p>
 * Two distinct, non-overlapping reasons keep a day out of {@link #observable()}, and they are
 * not folded together:
 * <ul>
 *     <li>{@link #oosCensored()} — the snapshot reference time is trustworthy but the day was
 *     genuinely stocked out ({@link #outOfStockFlag()} or zero available quantity). This is the
 *     only case that should ever contribute to the {@code OOS_CENSORED} quality flag.</li>
 *     <li>{@link #invalidSnapshotReference()} — the snapshot does not genuinely date the day it
 *     claims to represent (stale/mis-dated). This is a data-trust problem, not a stockout, and
 *     must not be reported as {@code OOS_CENSORED}.</li>
 * </ul>
 * A stocked-out day can never carry a positive {@code soldQuantity} (enforced below): real
 * OOS-censorship is always zero-sale, so it can never distort a raw window-sales total.
 */
public record DailyDemandObservation(
        LocalDate date,
        int onHandQuantity,
        int reservedQuantity,
        int soldQuantity,
        int transactionCount,
        int maxTransactionQuantity,
        boolean outOfStockFlag,
        OffsetDateTime snapshotAt
) {

    public DailyDemandObservation {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null.");
        }
        if (snapshotAt == null) {
            throw new IllegalArgumentException("snapshotAt must not be null.");
        }
        if (onHandQuantity < 0 || reservedQuantity < 0 || soldQuantity < 0
                || transactionCount < 0 || maxTransactionQuantity < 0) {
            throw new IllegalArgumentException("Quantities must not be negative.");
        }
        if (reservedQuantity > onHandQuantity) {
            throw new IllegalArgumentException("Reserved quantity must not exceed on-hand quantity.");
        }
        if (soldQuantity == 0) {
            if (transactionCount != 0 || maxTransactionQuantity != 0) {
                throw new IllegalArgumentException(
                        "transactionCount and maxTransactionQuantity must be zero when soldQuantity is zero.");
            }
        } else {
            if (transactionCount <= 0 || maxTransactionQuantity <= 0
                    || transactionCount > soldQuantity || maxTransactionQuantity > soldQuantity) {
                throw new IllegalArgumentException(
                        "transactionCount and maxTransactionQuantity must be positive and at most soldQuantity.");
            }
        }
        if ((outOfStockFlag || onHandQuantity - reservedQuantity == 0) && soldQuantity > 0) {
            throw new IllegalArgumentException(
                    "soldQuantity must be zero on a day that is out of stock (explicit flag or zero available quantity).");
        }
    }

    /**
     * Builds an observation whose {@code out_of_stock_flag} and {@code snapshot_at} follow the
     * V6 backfill convention: the flag derived from the quantity math, and the snapshot dated
     * exactly to {@code date}. Use the canonical constructor directly to model a stale/mis-dated
     * snapshot or an explicit flag that diverges from the quantity math.
     */
    public static DailyDemandObservation of(
            LocalDate date, int onHandQuantity, int reservedQuantity, int soldQuantity,
            int transactionCount, int maxTransactionQuantity) {
        boolean outOfStockFlag = onHandQuantity - reservedQuantity <= 0;
        OffsetDateTime snapshotAt = date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        return new DailyDemandObservation(date, onHandQuantity, reservedQuantity, soldQuantity,
                transactionCount, maxTransactionQuantity, outOfStockFlag, snapshotAt);
    }

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    /** The snapshot's own timestamp genuinely dates the day it claims to represent. */
    public boolean snapshotReferenceValid() {
        return snapshotAt.toLocalDate().equals(date);
    }

    /** Explicitly flagged, or computed as zero available-for-sale quantity. */
    public boolean stockedOut() {
        return outOfStockFlag || availableQuantity() == 0;
    }

    /** The snapshot is trustworthy but this is not a valid demand day: stale, or stocked out. */
    public boolean invalidSnapshotReference() {
        return !snapshotReferenceValid();
    }

    /** Per section 2: the inventory reference time was valid and sellable stock was at least one. */
    public boolean observable() {
        return snapshotReferenceValid() && !stockedOut();
    }

    /**
     * Per section 2: a trustworthy day whose zero sales cannot be used as demand evidence
     * because it was genuinely stocked out. Excludes {@link #invalidSnapshotReference()} days,
     * which are a separate data-trust problem, not a stockout.
     */
    public boolean oosCensored() {
        return snapshotReferenceValid() && stockedOut();
    }
}
