package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandEvent;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete, immutable input evidence for one MVP-2 Batch run, per
 * {@code data-model.md}'s Phase 3 mapping. {@link Mvp2InputAdapter#load} returns this only when
 * every anchor satisfies the input contract; otherwise it throws
 * {@link InputContractViolationException} and returns nothing. The pure calculation layer
 * consumes this graph directly and never queries the database itself.
 * <p>
 * The flat {@code events}/{@code inboundSchedules}/{@code openTransfers}/{@code routes} lists
 * are kept for compatibility, but a calculation loop should use the indexed
 * {@code *By*} maps -- they let the orchestration layer look up exactly one store-SKU's or
 * lane's evidence without scanning the whole graph. Both the maps and every list they contain
 * are immutable.
 */
public record Mvp2InputGraph(
        LocalDate analysisDate,
        String inputSnapshotVersion,
        List<Mvp2Anchor> anchors,
        List<DemandEvent> events,
        List<Mvp2InboundRow> inboundSchedules,
        List<Mvp2OpenTransferRow> openTransfers,
        List<Mvp2Route> routes,
        Map<Mvp2DonorSkuKey, Long> activeApprovedDraftQuantityByDonorSku,
        Map<Mvp2StoreSkuKey, List<DemandEvent>> eventsByStoreSku,
        Map<Mvp2StoreSkuKey, List<Mvp2InboundRow>> inboundByStoreSku,
        Map<Mvp2LaneKey, List<Mvp2OpenTransferRow>> openTransfersByLane,
        Map<Mvp2StorePairKey, List<Mvp2Route>> routesByStorePair
) {

    public Mvp2InputGraph {
        anchors = List.copyOf(anchors);
        events = List.copyOf(events);
        inboundSchedules = List.copyOf(inboundSchedules);
        openTransfers = List.copyOf(openTransfers);
        routes = List.copyOf(routes);
        // Collections.unmodifiableMap over a LinkedHashMap, not Map.copyOf: Map.copyOf does not
        // guarantee insertion order, which would silently discard the store/SKU/lane ordering
        // callers rely on for deterministic iteration.
        activeApprovedDraftQuantityByDonorSku =
                Collections.unmodifiableMap(new LinkedHashMap<>(activeApprovedDraftQuantityByDonorSku));
        eventsByStoreSku = deepImmutable(eventsByStoreSku);
        inboundByStoreSku = deepImmutable(inboundByStoreSku);
        openTransfersByLane = deepImmutable(openTransfersByLane);
        routesByStorePair = deepImmutable(routesByStorePair);
    }

    private static <K, V> Map<K, List<V>> deepImmutable(Map<K, List<V>> source) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
