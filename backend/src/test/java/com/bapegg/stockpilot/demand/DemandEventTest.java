package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemandEventTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 29);
    private static final LocalDate END = LocalDate.of(2026, 10, 7);

    @Test
    void completeUpliftIsRecognized() {
        DemandEvent event = new DemandEvent("EVENT-MVP2-GS02", "STORE-MVP2-RECEIVER-A",
                "SKU-MVP2-GS02-EVENT", START, END,
                new BigDecimal("1.20"), new BigDecimal("1.50"), new BigDecimal("1.80"));

        assertTrue(event.hasCompleteUplift());
        assertTrue(event.matchesStoreAndSku("STORE-MVP2-RECEIVER-A", "SKU-MVP2-GS02-EVENT"));
        assertFalse(event.matchesStoreAndSku("STORE-MVP2-DONOR-A", "SKU-MVP2-GS02-EVENT"));
    }

    @Test
    void missingAnyUpliftScenarioIsIncomplete() {
        DemandEvent event = new DemandEvent("EVENT-X", "STORE-A", "SKU-A", START, END,
                null, new BigDecimal("1.50"), new BigDecimal("1.80"));

        assertFalse(event.hasCompleteUplift());
    }

    @Test
    void overlapsUsesInclusiveBounds() {
        DemandEvent event = new DemandEvent("EVENT-X", "STORE-A", "SKU-A", START, END, null, null, null);

        assertTrue(event.overlaps(LocalDate.of(2026, 9, 1), START));
        assertTrue(event.overlaps(END, LocalDate.of(2026, 12, 31)));
        assertFalse(event.overlaps(LocalDate.of(2026, 9, 1), START.minusDays(1)));
        assertFalse(event.overlaps(END.plusDays(1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void startDateAfterEndDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", "STORE-A", "SKU-A", END, START, null, null, null));
    }

    @Test
    void nonPositiveUpliftIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", "STORE-A", "SKU-A", START, END,
                        BigDecimal.ZERO, new BigDecimal("1.5"), new BigDecimal("1.8")));
    }

    @Test
    void upliftOutOfOrderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", "STORE-A", "SKU-A", START, END,
                        new BigDecimal("1.5"), new BigDecimal("1.2"), new BigDecimal("1.8")));
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", "STORE-A", "SKU-A", START, END,
                        new BigDecimal("1.2"), new BigDecimal("1.8"), new BigDecimal("1.5")));
    }

    @Test
    void blankIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent(" ", "STORE-A", "SKU-A", START, END, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", " ", "SKU-A", START, END, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DemandEvent("EVENT-X", "STORE-A", " ", START, END, null, null, null));
    }
}
