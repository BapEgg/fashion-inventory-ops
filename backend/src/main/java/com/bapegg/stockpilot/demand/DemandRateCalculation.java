package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic {@code low/base/high} baseline demand rate calculation for one
 * store-SKU, per {@code knowledge/business-rules.md} section 5. Independent of Spring/JPA.
 * <p>
 * Computes the historical baseline only. Event-uplift multiplication is a separate, later
 * step ({@link DemandEvent#upliftFor} / {@link #applyUplift}) applied once a specific
 * scenario's arrival-through-target-coverage window is known (order item 6); this class does
 * not know that window and must not guess it from the plan horizon used for signal
 * classification.
 */
public record DemandRateCalculation(
        List<BigDecimal> eligibleWeeklyRates,
        BigDecimal lowDemandRate,
        BigDecimal baseDemandRate,
        BigDecimal highDemandRate,
        boolean reviewRequired
) {

    /**
     * @param signalType    the store-SKU's already-decided primary signal
     *                      ({@link DemandSignalClassification#classify}). Section 3 checks
     *                      {@code KNOWN_EVENT} before {@code UNEXPLAINED_SPIKE}, so a day that
     *                      merely looks statistically spike-shaped is not necessarily the
     *                      classified anomaly: {@link DemandObservationStatistics#spikeEvidenceDate()}
     *                      is excluded from the baseline only when {@code signalType} actually
     *                      is {@code UNEXPLAINED_SPIKE}, never unconditionally.
     * @param relevantEvents the same relevant-event set the classifier used, so their date
     *                       ranges are excluded from the baseline regardless of which signal won
     */
    public static DemandRateCalculation calculate(
            DemandObservationWindow window,
            DemandObservationStatistics stats,
            DemandSignalType signalType,
            List<DemandEvent> relevantEvents) {
        List<DailyDemandObservation> days = window.days();
        boolean excludeSpikeEvidenceDay = signalType == DemandSignalType.UNEXPLAINED_SPIKE;

        List<BigDecimal> weeklyRates = new ArrayList<>();
        for (int week = 0; week < DemandAnalysisRules.FIXED_WEEK_COUNT; week++) {
            long eligibleSales = 0;
            int eligibleDays = 0;
            for (int offset = 0; offset < DemandAnalysisRules.DAYS_PER_WEEK; offset++) {
                DailyDemandObservation day = days.get(week * DemandAnalysisRules.DAYS_PER_WEEK + offset);
                if (!day.observable()) {
                    continue;
                }
                if (excludeSpikeEvidenceDay && day.date().equals(stats.spikeEvidenceDate())) {
                    continue;
                }
                if (relevantEvents.stream().anyMatch(event -> event.overlaps(day.date(), day.date()))) {
                    continue;
                }
                eligibleSales += day.soldQuantity();
                eligibleDays++;
            }
            if (eligibleDays >= 1) {
                weeklyRates.add(scale(BigDecimal.valueOf(eligibleSales)
                        .divide(BigDecimal.valueOf(eligibleDays), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP)));
            }
        }

        if (weeklyRates.size() < DemandAnalysisRules.MINIMUM_VALID_WEEKLY_RATES) {
            return new DemandRateCalculation(List.copyOf(weeklyRates), null, null, null, true);
        }

        List<BigDecimal> sorted = weeklyRates.stream().sorted().toList();
        BigDecimal low = quantile(sorted, DemandAnalysisRules.LOW_DEMAND_RATE_PERCENTILE);
        BigDecimal base = quantile(sorted, DemandAnalysisRules.BASE_DEMAND_RATE_PERCENTILE);
        BigDecimal high = quantile(sorted, DemandAnalysisRules.HIGH_DEMAND_RATE_PERCENTILE);
        return new DemandRateCalculation(sorted, low, base, high, false);
    }

    /**
     * Linear-interpolation percentile over an ascending-sorted list, per section 5:
     * {@code h = (n-1)*p; j = floor(h); g = h-j; quantile = x[j]*(1-g) + x[min(j+1,n-1)]*g}.
     */
    private static BigDecimal quantile(List<BigDecimal> sortedAscending, BigDecimal p) {
        int n = sortedAscending.size();
        BigDecimal h = BigDecimal.valueOf(n - 1).multiply(p);
        int j = h.setScale(0, RoundingMode.FLOOR).intValue();
        BigDecimal g = h.subtract(BigDecimal.valueOf(j));
        BigDecimal lower = sortedAscending.get(j);
        BigDecimal upper = sortedAscending.get(Math.min(j + 1, n - 1));
        BigDecimal result = lower.multiply(BigDecimal.ONE.subtract(g)).add(upper.multiply(g));
        return scale(result);
    }

    /**
     * Multiplies a base demand rate by one of an applicable event's uplift factors, rounding to
     * scale 12 HALF_UP immediately after -- per section 5, a 2-decimal display rounding must
     * never be reused as an input to this multiplication.
     */
    public static BigDecimal applyUplift(BigDecimal rate, BigDecimal upliftFactor) {
        return scale(rate.multiply(upliftFactor));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP);
    }
}
