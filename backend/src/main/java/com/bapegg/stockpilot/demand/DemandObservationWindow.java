package com.bapegg.stockpilot.demand;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The fixed 28-day observation window for one store-SKU, per
 * {@code knowledge/business-rules.md} section 2: {@code [analysisDate - 28d, analysisDate - 1d]},
 * excluding the analysis date itself. Immutable and independent of Spring/JPA.
 */
public record DemandObservationWindow(
        LocalDate analysisDate,
        LocalDate productLaunchDate,
        List<DailyDemandObservation> days
) {

    public DemandObservationWindow {
        if (analysisDate == null || productLaunchDate == null) {
            throw new IllegalArgumentException("analysisDate and productLaunchDate must not be null.");
        }
        if (productLaunchDate.isAfter(analysisDate)) {
            throw new IllegalArgumentException("productLaunchDate must not be after analysisDate.");
        }
        if (days == null || days.size() != DemandAnalysisRules.OBSERVATION_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                    "days must contain exactly " + DemandAnalysisRules.OBSERVATION_WINDOW_DAYS + " entries.");
        }
        days = List.copyOf(days);

        LocalDate expectedDate = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        for (DailyDemandObservation day : days) {
            if (!day.date().equals(expectedDate)) {
                throw new IllegalArgumentException(
                        "days must be exactly the " + DemandAnalysisRules.OBSERVATION_WINDOW_DAYS
                                + " consecutive dates ending the day before analysisDate; expected "
                                + expectedDate + " but found " + day.date() + ".");
            }
            expectedDate = expectedDate.plusDays(1);
        }
    }

    /** Whole calendar days between product launch and the analysis date. */
    public long launchDaysElapsed() {
        return ChronoUnit.DAYS.between(productLaunchDate, analysisDate);
    }
}
