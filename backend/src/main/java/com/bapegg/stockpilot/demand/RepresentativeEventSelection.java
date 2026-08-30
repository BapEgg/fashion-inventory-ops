package com.bapegg.stockpilot.demand;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure deterministic relevant/representative event selection for one store-SKU, per
 * {@code knowledge/business-rules.md} section 10's shared current-basis contract: "관련 이벤트는
 * 같은 입력 버전에서 관측 또는 계획 구간과 겹치는 store–SKU 행을 (startDate, eventCode) 오름차순으로
 * 정렬하고 첫 행을 대표 이벤트로 고른다." Batch signal classification
 * ({@link DemandSignalClassification#classify}), the approval transaction and `MANUAL` preview
 * all call this same method so none of the three can silently disagree about which event applies.
 * <p>
 * Independent of Spring/JPA. Callers are responsible for scoping {@code events} to the correct
 * {@code inputSnapshotVersion} before calling -- this class only filters by store/SKU and window
 * overlap.
 */
public final class RepresentativeEventSelection {

    private RepresentativeEventSelection() {
    }

    /**
     * @param windowAStart first relevance window (the shared 28-day observation window)
     * @param windowBStart second relevance window (the shared full plan horizon); after this
     *                     representative event is selected, callers separately test whether it
     *                     overlaps a route's arrival-through-target-coverage window before
     *                     applying that route's uplift
     * @return every matching event, ascending by {@code (startDate, eventCode)} -- the first
     *         element (if any) is the representative event; the full list is also what
     *         {@link DemandRateCalculation#calculate} excludes from the historical baseline
     */
    public static List<DemandEvent> selectRelevant(
            String storeId,
            String skuId,
            LocalDate windowAStart,
            LocalDate windowAEnd,
            LocalDate windowBStart,
            LocalDate windowBEnd,
            List<DemandEvent> events) {
        return events.stream()
                .filter(event -> event.matchesStoreAndSku(storeId, skuId))
                .filter(event -> event.overlaps(windowAStart, windowAEnd) || event.overlaps(windowBStart, windowBEnd))
                .sorted(Comparator.comparing(DemandEvent::startDate).thenComparing(DemandEvent::eventCode))
                .toList();
    }

    /** The first (earliest, then lowest {@code eventCode}) event in an already-selected relevant list. */
    public static Optional<DemandEvent> representative(List<DemandEvent> sortedRelevantEvents) {
        return sortedRelevantEvents.stream().findFirst();
    }
}
