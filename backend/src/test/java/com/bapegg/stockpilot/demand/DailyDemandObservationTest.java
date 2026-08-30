package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyDemandObservationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);
    private static final OffsetDateTime VALID_SNAPSHOT_AT = DAY.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

    @Test
    void positiveAvailableQuantityIsObservable() {
        DailyDemandObservation day = DailyDemandObservation.of(DAY, 20, 0, 2, 2, 1);

        assertEquals(20, day.availableQuantity());
        assertTrue(day.observable());
        assertFalse(day.oosCensored());
        assertFalse(day.invalidSnapshotReference());
    }

    @Test
    void zeroAvailableQuantityIsOosCensoredNotInvalid() {
        DailyDemandObservation day = DailyDemandObservation.of(DAY, 0, 0, 0, 0, 0);

        assertEquals(0, day.availableQuantity());
        assertTrue(day.stockedOut());
        assertFalse(day.observable());
        assertTrue(day.oosCensored());
        assertFalse(day.invalidSnapshotReference());
    }

    @Test
    void explicitOutOfStockFlagOverridesPositiveAvailableQuantity() {
        // e.g. damaged or held stock: the ledger still shows 20 available, but the store
        // explicitly reports the day as out of stock. Section 2 requires honoring that
        // explicit input, not re-deriving observability purely from the quantity math.
        DailyDemandObservation day = new DailyDemandObservation(DAY, 20, 0, 0, 0, 0, true, VALID_SNAPSHOT_AT);

        assertEquals(20, day.availableQuantity());
        assertTrue(day.stockedOut());
        assertFalse(day.observable());
        assertTrue(day.oosCensored());
    }

    @Test
    void explicitOutOfStockFlagWithPositiveSalesIsRejected() {
        // A day explicitly flagged as having no sellable stock cannot also carry a recorded
        // sale: real OOS-censorship is always zero-sale (business-rules.md section 2), so this
        // combination is a data contradiction, not a state the domain silently tolerates.
        assertThrows(IllegalArgumentException.class,
                () -> new DailyDemandObservation(DAY, 20, 0, 2, 2, 1, true, VALID_SNAPSHOT_AT));
    }

    @Test
    void zeroAvailableQuantityWithPositiveSalesIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyDemandObservation(DAY, 5, 5, 2, 2, 1, false, VALID_SNAPSHOT_AT));
    }

    @Test
    void mismatchedSnapshotDateIsInvalidNotOosCensored() {
        // A stale/mis-dated snapshot_at is a data-trust problem, not a stockout, and must not
        // be folded into the OOS_CENSORED quality flag.
        OffsetDateTime staleSnapshotAt = DAY.minusDays(3).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        DailyDemandObservation day = new DailyDemandObservation(DAY, 20, 0, 2, 2, 1, false, staleSnapshotAt);

        assertFalse(day.snapshotReferenceValid());
        assertFalse(day.stockedOut());
        assertFalse(day.observable());
        assertTrue(day.invalidSnapshotReference());
        assertFalse(day.oosCensored());
    }

    @Test
    void nullSnapshotAtIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyDemandObservation(DAY, 20, 0, 2, 2, 1, false, null));
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, -1, 0, 0, 0, 0));
    }

    @Test
    void reservedExceedingOnHandIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, 5, 6, 0, 0, 0));
    }

    @Test
    void zeroSoldWithNonZeroTransactionCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, 20, 0, 0, 1, 0));
    }

    @Test
    void positiveSoldWithZeroTransactionCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, 20, 0, 2, 0, 1));
    }

    @Test
    void transactionCountExceedingSoldQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, 20, 0, 2, 3, 1));
    }

    @Test
    void maxTransactionQuantityExceedingSoldQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DailyDemandObservation.of(DAY, 20, 0, 2, 1, 3));
    }
}
