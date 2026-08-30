package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepresentativeEventSelectionTest {

    private static final String STORE = "STORE-R";
    private static final String SKU = "SKU-1";

    @Test
    void picksTheEarliestStartDateAsRepresentative() {
        DemandEvent early = event("EVT-B", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));
        DemandEvent later = event("EVT-A", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15));

        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                STORE, SKU, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), List.of(later, early));

        assertEquals(List.of(early, later), relevant);
        assertEquals(Optional.of(early), RepresentativeEventSelection.representative(relevant));
    }

    @Test
    void tieBreaksEqualStartDatesByEventCodeAscending() {
        DemandEvent codeB = event("EVT-B", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));
        DemandEvent codeA = event("EVT-A", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                STORE, SKU, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), List.of(codeB, codeA));

        assertEquals(List.of(codeA, codeB), relevant);
        assertEquals(Optional.of(codeA), RepresentativeEventSelection.representative(relevant));
    }

    @Test
    void excludesEventsForADifferentStoreOrSku() {
        DemandEvent wrongStore = new DemandEvent("EVT-X", "STORE-OTHER", SKU,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null, null, null);
        DemandEvent wrongSku = new DemandEvent("EVT-Y", STORE, "SKU-OTHER",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null, null, null);

        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                STORE, SKU, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), List.of(wrongStore, wrongSku));

        assertTrue(relevant.isEmpty());
        assertEquals(Optional.empty(), RepresentativeEventSelection.representative(relevant));
    }

    @Test
    void excludesEventsOverlappingNeitherWindow() {
        DemandEvent outside = event("EVT-Z", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));

        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                STORE, SKU, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), List.of(outside));

        assertTrue(relevant.isEmpty());
    }

    @Test
    void includesAnEventOverlappingOnlyTheSecondWindow() {
        DemandEvent onlyWindowB = event("EVT-W", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8));

        List<DemandEvent> relevant = RepresentativeEventSelection.selectRelevant(
                STORE, SKU, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), List.of(onlyWindowB));

        assertEquals(List.of(onlyWindowB), relevant);
    }

    private static DemandEvent event(String eventCode, LocalDate start, LocalDate end) {
        return new DemandEvent(eventCode, STORE, SKU, start, end, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }
}
