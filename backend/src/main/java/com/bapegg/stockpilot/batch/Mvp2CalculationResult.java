package com.bapegg.stockpilot.batch;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete, immutable in-memory result of one MVP-2 Batch calculation run, per
 * {@code data-model.md}'s Phase 3 mapping. Produced entirely by
 * {@link Mvp2CalculationOrchestrator#calculate} from an {@link Mvp2InputGraph} -- no JPA entity,
 * repository, {@code JdbcTemplate} or Spring Batch type appears anywhere in this result. Entity
 * conversion and atomic persistence remain a separate boundary handled by
 * {@link Mvp2AtomicOutputWriter}.
 */
public record Mvp2CalculationResult(
        LocalDate analysisDate,
        String inputSnapshotVersion,
        List<Mvp2MetricResult> metrics,
        List<Mvp2CandidateResult> candidates,
        Map<Mvp2StoreSkuKey, Mvp2MetricResult> metricsByStoreSku,
        Map<Mvp2StoreSkuKey, List<Mvp2CandidateResult>> candidatesByReceiver
) {

    public Mvp2CalculationResult {
        metrics = List.copyOf(metrics);
        candidates = List.copyOf(candidates);
        // Collections.unmodifiableMap over a LinkedHashMap, not Map.copyOf: Map.copyOf does not
        // guarantee insertion order, which would silently discard the (storeId, skuId)
        // ordering callers rely on for deterministic iteration.
        metricsByStoreSku = Collections.unmodifiableMap(new LinkedHashMap<>(metricsByStoreSku));
        candidatesByReceiver = deepImmutable(candidatesByReceiver);
    }

    private static <K, V> Map<K, List<V>> deepImmutable(Map<K, List<V>> source) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
