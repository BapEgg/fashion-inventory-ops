package com.bapegg.stockpilot.demand;

import java.time.LocalDate;
import java.util.List;

/**
 * The forward-looking plan horizon used to decide whether a demand event is relevant, per
 * {@code knowledge/business-rules.md} section 3: from {@code analysisDate} through the largest
 * {@code leadTimeDays + receiverTargetCoverageDays} across the store-SKU's active inbound
 * routes, or a flat {@value DemandAnalysisRules#PLAN_HORIZON_NO_ROUTE_FALLBACK_DAYS}-day
 * {@code ASSUMPTION} fallback when there is no active route at all.
 */
public record PlanHorizon(LocalDate analysisDate, LocalDate endDate) {

    public PlanHorizon {
        if (analysisDate == null || endDate == null) {
            throw new IllegalArgumentException("analysisDate and endDate must not be null.");
        }
        if (endDate.isBefore(analysisDate)) {
            throw new IllegalArgumentException("endDate must not be before analysisDate.");
        }
    }

    /**
     * @param receiverTargetCoverageDays the store-SKU's target coverage days, applied once per
     *                                   active route (constant across routes for a given
     *                                   receiver-SKU, since it is a policy property, not a
     *                                   per-route one)
     * @param activeRouteLeadTimeDays    lead time, in days, of each active route into this
     *                                   receiver; empty when no route is active
     */
    public static PlanHorizon of(LocalDate analysisDate, int receiverTargetCoverageDays,
            List<Integer> activeRouteLeadTimeDays) {
        // Mirror V6's ck_sp_policy_values (target_coverage_days >= 0) and ck_sp_route_values
        // (lead_time_days >= 0): a negative input here is a data error, not a valid horizon.
        if (receiverTargetCoverageDays < 0) {
            throw new IllegalArgumentException("receiverTargetCoverageDays must not be negative.");
        }
        if (activeRouteLeadTimeDays == null || activeRouteLeadTimeDays.isEmpty()) {
            return new PlanHorizon(analysisDate,
                    analysisDate.plusDays(DemandAnalysisRules.PLAN_HORIZON_NO_ROUTE_FALLBACK_DAYS));
        }
        if (activeRouteLeadTimeDays.stream().anyMatch(leadTimeDays -> leadTimeDays == null || leadTimeDays < 0)) {
            throw new IllegalArgumentException("activeRouteLeadTimeDays must not contain a null or negative value.");
        }
        int longestLeadTimeDays = activeRouteLeadTimeDays.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow();
        return new PlanHorizon(analysisDate, analysisDate.plusDays(longestLeadTimeDays + receiverTargetCoverageDays));
    }

    public boolean overlaps(LocalDate rangeStartInclusive, LocalDate rangeEndInclusive) {
        return !analysisDate.isAfter(rangeEndInclusive) && !endDate.isBefore(rangeStartInclusive);
    }
}
