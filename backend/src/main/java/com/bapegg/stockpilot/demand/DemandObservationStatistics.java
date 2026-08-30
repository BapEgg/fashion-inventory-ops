package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure deterministic observation statistics and the {@code OOS_CENSORED} quality flag for one
 * store-SKU's {@link DemandObservationWindow}, per {@code knowledge/business-rules.md}
 * sections 2-4. Independent of Spring/JPA; storage rounding to the column scale happens only
 * at the JPA entity boundary, as with {@code InventoryMetricCalculation}.
 * <p>
 * Demand signal classification itself (section 3's decision tree, which also needs event and
 * plan-horizon input) and low/base/high demand rates (section 5) are out of scope here; this
 * class computes only the statistics those later steps read.
 * <p>
 * {@code oosCensoredDayCount}/{@code oosCensored} count only real, trustworthy stockouts
 * ({@link DailyDemandObservation#oosCensored()}); a day whose snapshot reference time is
 * untrustworthy ({@link DailyDemandObservation#invalidSnapshotReference()}) is excluded from
 * every statistic here, including {@code totalWindowSales}, but is tracked separately in
 * {@code invalidSnapshotDayCount} rather than folded into the OOS count.
 */
public record DemandObservationStatistics(
        int observableDayCount,
        int oosCensoredDayCount,
        boolean oosCensored,
        int invalidSnapshotDayCount,
        int activeWeekCount,
        BigDecimal salesDayRatio,
        BigDecimal weeklyCoefficientOfVariation,
        Integer maxDailySales,
        BigDecimal medianDailySales,
        BigDecimal madDailySales,
        long totalWindowSales,
        BigDecimal spikeThreshold,
        boolean spikeCandidate,
        LocalDate spikeEvidenceDate,
        int maxTransactionQuantityInWindow,
        boolean singleBulkTransaction,
        long launchDaysElapsed
) {

    public static DemandObservationStatistics calculate(DemandObservationWindow window) {
        List<DailyDemandObservation> days = window.days();

        List<DailyDemandObservation> observableDays = new ArrayList<>();
        int oosCensoredDayCount = 0;
        int invalidSnapshotDayCount = 0;
        long totalWindowSales = 0;
        for (DailyDemandObservation day : days) {
            if (day.observable()) {
                observableDays.add(day);
                totalWindowSales += day.soldQuantity();
            } else if (day.oosCensored()) {
                // Real stockout: the constructor guarantees soldQuantity is zero here, so this
                // never distorts totalWindowSales.
                oosCensoredDayCount++;
            } else {
                // Untrustworthy reference time, not a stockout: excluded from every demand
                // statistic, including the raw window-sales total.
                invalidSnapshotDayCount++;
            }
        }
        int observableDayCount = observableDays.size();

        int activeWeekCount = countActiveWeeks(days);
        BigDecimal salesDayRatio = salesDayRatio(observableDays);
        BigDecimal weeklyCoefficientOfVariation = weeklyCoefficientOfVariation(days);

        List<BigDecimal> sortedDailySales = observableDays.stream()
                .map(DailyDemandObservation::soldQuantity)
                .map(BigDecimal::valueOf)
                .sorted()
                .toList();
        BigDecimal medianDailySales = median(sortedDailySales);
        BigDecimal madDailySales = medianAbsoluteDeviation(sortedDailySales, medianDailySales);
        Integer maxDailySales = observableDays.isEmpty()
                ? null
                : observableDays.stream().mapToInt(DailyDemandObservation::soldQuantity).max().getAsInt();

        BigDecimal spikeThreshold = null;
        boolean spikeCandidate = false;
        LocalDate spikeEvidenceDate = null;
        int maxTransactionQuantityInWindow = observableDays.stream()
                .mapToInt(DailyDemandObservation::maxTransactionQuantity)
                .max()
                .orElse(0);
        boolean singleBulkTransaction = false;

        if (maxDailySales != null) {
            spikeThreshold = scale(DemandAnalysisRules.SPIKE_ABSOLUTE_MINIMUM
                    .max(medianDailySales.add(DemandAnalysisRules.SPIKE_MAD_MULTIPLIER
                            .multiply(madDailySales.max(DemandAnalysisRules.SPIKE_MINIMUM_MAD)))));

            boolean overThreshold = BigDecimal.valueOf(maxDailySales).compareTo(spikeThreshold) >= 0;
            boolean hasWindowSales = totalWindowSales > 0;
            boolean overWindowShare = hasWindowSales
                    && scale(BigDecimal.valueOf(maxDailySales)
                            .divide(BigDecimal.valueOf(totalWindowSales), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP))
                            .compareTo(DemandAnalysisRules.SPIKE_WINDOW_SHARE_MINIMUM) >= 0;
            spikeCandidate = overThreshold && hasWindowSales && overWindowShare;

            if (spikeCandidate) {
                spikeEvidenceDate = observableDays.stream()
                        .filter(day -> day.soldQuantity() == maxDailySales)
                        .map(DailyDemandObservation::date)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
            }

            if (maxDailySales > 0 && maxTransactionQuantityInWindow >= DemandAnalysisRules.BULK_TRANSACTION_MINIMUM_QUANTITY.intValueExact()) {
                BigDecimal transactionShare = scale(BigDecimal.valueOf(maxTransactionQuantityInWindow)
                        .divide(BigDecimal.valueOf(maxDailySales), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP));
                singleBulkTransaction = transactionShare.compareTo(DemandAnalysisRules.BULK_TRANSACTION_SHARE_MINIMUM) >= 0;
            }
        }

        return new DemandObservationStatistics(
                observableDayCount,
                oosCensoredDayCount,
                oosCensoredDayCount > 0,
                invalidSnapshotDayCount,
                activeWeekCount,
                salesDayRatio,
                weeklyCoefficientOfVariation,
                maxDailySales,
                medianDailySales,
                madDailySales,
                totalWindowSales,
                spikeThreshold,
                spikeCandidate,
                spikeEvidenceDate,
                maxTransactionQuantityInWindow,
                singleBulkTransaction,
                window.launchDaysElapsed()
        );
    }

    private static int countActiveWeeks(List<DailyDemandObservation> days) {
        int activeWeeks = 0;
        for (int week = 0; week < DemandAnalysisRules.FIXED_WEEK_COUNT; week++) {
            long weekSales = 0;
            for (int offset = 0; offset < DemandAnalysisRules.DAYS_PER_WEEK; offset++) {
                DailyDemandObservation day = days.get(week * DemandAnalysisRules.DAYS_PER_WEEK + offset);
                if (day.observable()) {
                    weekSales += day.soldQuantity();
                }
            }
            if (weekSales >= 1) {
                activeWeeks++;
            }
        }
        return activeWeeks;
    }

    private static BigDecimal salesDayRatio(List<DailyDemandObservation> observableDays) {
        if (observableDays.isEmpty()) {
            return null;
        }
        long salesDays = observableDays.stream().filter(day -> day.soldQuantity() >= 1).count();
        return BigDecimal.valueOf(salesDays)
                .divide(BigDecimal.valueOf(observableDays.size()), DemandAnalysisRules.SALES_DAY_RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal weeklyCoefficientOfVariation(List<DailyDemandObservation> days) {
        BigDecimal[] weeklySums = new BigDecimal[DemandAnalysisRules.FIXED_WEEK_COUNT];
        for (int week = 0; week < DemandAnalysisRules.FIXED_WEEK_COUNT; week++) {
            long weekSales = 0;
            for (int offset = 0; offset < DemandAnalysisRules.DAYS_PER_WEEK; offset++) {
                DailyDemandObservation day = days.get(week * DemandAnalysisRules.DAYS_PER_WEEK + offset);
                if (day.observable()) {
                    weekSales += day.soldQuantity();
                }
            }
            weeklySums[week] = BigDecimal.valueOf(weekSales);
        }

        BigDecimal mean = scale(sum(weeklySums).divide(
                BigDecimal.valueOf(DemandAnalysisRules.FIXED_WEEK_COUNT), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP));
        if (mean.signum() == 0) {
            return null;
        }

        BigDecimal[] squaredDeviations = new BigDecimal[DemandAnalysisRules.FIXED_WEEK_COUNT];
        for (int i = 0; i < weeklySums.length; i++) {
            BigDecimal deviation = weeklySums[i].subtract(mean);
            squaredDeviations[i] = deviation.multiply(deviation);
        }
        BigDecimal variance = sum(squaredDeviations)
                .divide(BigDecimal.valueOf(DemandAnalysisRules.FIXED_WEEK_COUNT), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal populationStandardDeviation = variance.sqrt(new MathContext(2 * DemandAnalysisRules.RATE_SCALE));

        return scale(populationStandardDeviation.divide(mean, DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal sum(BigDecimal[] values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    /**
     * Standard median, equivalent to {@code business-rules.md} section 5's linear-interpolation
     * quantile formula evaluated at {@code p = 0.5}.
     */
    private static BigDecimal median(List<BigDecimal> sortedAscending) {
        int n = sortedAscending.size();
        if (n == 0) {
            return null;
        }
        if (n % 2 == 1) {
            return scale(sortedAscending.get(n / 2));
        }
        BigDecimal lower = sortedAscending.get(n / 2 - 1);
        BigDecimal upper = sortedAscending.get(n / 2);
        return scale(lower.add(upper).divide(BigDecimal.valueOf(2), DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal medianAbsoluteDeviation(List<BigDecimal> sortedDailySales, BigDecimal median) {
        if (median == null) {
            return null;
        }
        List<BigDecimal> absoluteDeviations = sortedDailySales.stream()
                .map(value -> value.subtract(median).abs())
                .sorted()
                .toList();
        return median(absoluteDeviations);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP);
    }
}
