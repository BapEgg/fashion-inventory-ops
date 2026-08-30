package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One {@code SP_DEMAND_EVENT} row, per {@code knowledge/business-rules.md} sections 1-5.
 * Immutable and independent of Spring/JPA. Uplift is a synthetic {@code low/base/high} input,
 * never predicted by Java or AI (project.md, business-rules.md section 11).
 */
public record DemandEvent(
        String eventCode,
        String storeId,
        String skuId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal upliftLow,
        BigDecimal upliftBase,
        BigDecimal upliftHigh
) {

    public DemandEvent {
        if (eventCode == null || eventCode.isBlank()) {
            throw new IllegalArgumentException("eventCode must not be blank.");
        }
        if (storeId == null || storeId.isBlank() || skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("storeId and skuId must not be blank.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate.");
        }
        if (upliftLow != null && upliftLow.signum() <= 0) {
            throw new IllegalArgumentException("upliftLow must be positive when present.");
        }
        if (upliftBase != null && upliftBase.signum() <= 0) {
            throw new IllegalArgumentException("upliftBase must be positive when present.");
        }
        if (upliftHigh != null && upliftHigh.signum() <= 0) {
            throw new IllegalArgumentException("upliftHigh must be positive when present.");
        }
        if (upliftLow != null && upliftBase != null && upliftLow.compareTo(upliftBase) > 0) {
            throw new IllegalArgumentException("upliftLow must be at most upliftBase.");
        }
        if (upliftBase != null && upliftHigh != null && upliftBase.compareTo(upliftHigh) > 0) {
            throw new IllegalArgumentException("upliftBase must be at most upliftHigh.");
        }
    }

    /** Per section 4's INCOMPLETE_EVENT_DATA flag: all three uplift scenarios must be present. */
    public boolean hasCompleteUplift() {
        return upliftLow != null && upliftBase != null && upliftHigh != null;
    }

    public boolean matchesStoreAndSku(String storeId, String skuId) {
        return this.storeId.equals(storeId) && this.skuId.equals(skuId);
    }

    public boolean overlaps(LocalDate rangeStartInclusive, LocalDate rangeEndInclusive) {
        return !startDate.isAfter(rangeEndInclusive) && !endDate.isBefore(rangeStartInclusive);
    }

    /**
     * Per section 5: this event's uplift applies to a specific scenario's demand rate only
     * when the event has complete uplift and overlaps that scenario's own arrival-through-
     * target-coverage window -- never the (already elapsed) observation window. This method
     * only decides applicability; the caller multiplies each rate by the returned factor and
     * rounds to scale 12 HALF_UP immediately after (see {@link DemandRateCalculation#applyUplift}).
     */
    public Optional<UpliftFactors> upliftFor(LocalDate scenarioWindowStart, LocalDate scenarioWindowEnd) {
        if (!hasCompleteUplift() || !overlaps(scenarioWindowStart, scenarioWindowEnd)) {
            return Optional.empty();
        }
        return Optional.of(new UpliftFactors(upliftLow, upliftBase, upliftHigh));
    }

    public record UpliftFactors(BigDecimal low, BigDecimal base, BigDecimal high) {
    }
}
