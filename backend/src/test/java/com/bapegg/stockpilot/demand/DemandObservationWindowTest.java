package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemandObservationWindowTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate LAUNCH_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void validWindowComputesLaunchDaysElapsed() {
        DemandObservationWindow window = new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, flatDays(2));

        assertEquals(272, window.launchDaysElapsed());
    }

    @Test
    void wrongDayCountIsRejected() {
        List<DailyDemandObservation> tooFew = flatDays(2).subList(0, 27);

        assertThrows(IllegalArgumentException.class,
                () -> new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, tooFew));
    }

    @Test
    void nonConsecutiveDatesAreRejected() {
        List<DailyDemandObservation> days = new ArrayList<>(flatDays(2));
        days.set(10, DailyDemandObservation.of(days.get(10).date().plusDays(1), 20, 0, 2, 2, 1));

        assertThrows(IllegalArgumentException.class,
                () -> new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, days));
    }

    @Test
    void windowNotEndingTheDayBeforeAnalysisDateIsRejected() {
        List<DailyDemandObservation> shiftedByOneDay = flatDays(2).stream()
                .map(day -> DailyDemandObservation.of(day.date().minusDays(1), 20, 0, 2, 2, 1))
                .toList();

        assertThrows(IllegalArgumentException.class,
                () -> new DemandObservationWindow(ANALYSIS_DATE, LAUNCH_DATE, shiftedByOneDay));
    }

    @Test
    void launchDateAfterAnalysisDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemandObservationWindow(ANALYSIS_DATE, ANALYSIS_DATE.plusDays(1), flatDays(2)));
    }

    private static List<DailyDemandObservation> flatDays(int soldQuantityPerDay) {
        List<DailyDemandObservation> days = new ArrayList<>();
        LocalDate date = ANALYSIS_DATE.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (int i = 0; i < DemandAnalysisRules.OBSERVATION_WINDOW_DAYS; i++) {
            days.add(soldQuantityPerDay == 0
                    ? DailyDemandObservation.of(date, 20, 0, 0, 0, 0)
                    : DailyDemandObservation.of(date, 20, 0, soldQuantityPerDay, soldQuantityPerDay, 1));
            date = date.plusDays(1);
        }
        return days;
    }
}
