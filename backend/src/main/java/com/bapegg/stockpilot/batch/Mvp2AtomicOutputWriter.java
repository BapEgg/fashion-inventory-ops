package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlag;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlagRepository;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.SpCandidateReason;
import com.bapegg.stockpilot.rebalance.SpCandidateReasonRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenario;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts one accepted {@link Mvp2CalculationResult} into the existing V1-V13 JPA entities and
 * persists it atomically, per current-task.md section 4's nine ordered steps. Structural
 * validation here only protects flat/index consistency and FK linkage between the result's own
 * parts and the claimed run -- it never re-judges candidate eligibility, reasons, or scenario
 * quantities/risk; that is the pure calculation contract's job (proven by
 * {@code Mvp2CalculationOrchestratorTest} and the Golden Scenario tests), with DB unique/check
 * constraints as the final defense.
 */
@Service
public class Mvp2AtomicOutputWriter {

    private final SpAnalysisRunRepository analysisRunRepository;
    private final SpInventoryMetricRepository metricRepository;
    private final SpInventorySnapshotRepository snapshotRepository;
    private final SpMetricQualityFlagRepository qualityFlagRepository;
    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpCandidateReasonRepository candidateReasonRepository;
    private final SpRebalanceScenarioRepository scenarioRepository;

    public Mvp2AtomicOutputWriter(
            SpAnalysisRunRepository analysisRunRepository,
            SpInventoryMetricRepository metricRepository,
            SpInventorySnapshotRepository snapshotRepository,
            SpMetricQualityFlagRepository qualityFlagRepository,
            SpRebalanceRecommendationRepository recommendationRepository,
            SpCandidateReasonRepository candidateReasonRepository,
            SpRebalanceScenarioRepository scenarioRepository) {
        this.analysisRunRepository = analysisRunRepository;
        this.metricRepository = metricRepository;
        this.snapshotRepository = snapshotRepository;
        this.qualityFlagRepository = qualityFlagRepository;
        this.recommendationRepository = recommendationRepository;
        this.candidateReasonRepository = candidateReasonRepository;
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional
    public void writeAndComplete(Long analysisRunId, Mvp2CalculationResult result) {
        // Step 1: write-lock-read the run, verify RUNNING.
        SpAnalysisRun run = analysisRunRepository.lockById(analysisRunId)
                .orElseThrow(() -> new Mvp2OutputContractViolationException(
                        "No sp_analysis_run row found for id " + analysisRunId + "."));
        if (run.getRunStatus() != AnalysisRunStatus.RUNNING) {
            throw new Mvp2OutputContractViolationException("Run " + analysisRunId + " is not RUNNING (was "
                    + run.getRunStatus() + "); the atomic writer only writes a freshly claimed run.");
        }

        // Step 2: structural validation against the claimed run.
        validateResultMatchesRun(run, result);

        // Step 3: one bulk snapshot read -> store-SKU map; every metric key must resolve.
        Map<Mvp2StoreSkuKey, SpInventorySnapshot> snapshotByKey = loadSnapshotMap(run, result);

        // Step 4: never overwrite a partial result.
        if (metricRepository.countByAnalysisRun_AnalysisRunId(analysisRunId) != 0) {
            throw new Mvp2OutputContractViolationException(
                    "Run " + analysisRunId + " already has persisted metrics; a partial result is never overwritten.");
        }

        // Step 5: metrics, then quality flags.
        Map<Mvp2StoreSkuKey, SpInventoryMetric> metricByKey = writeMetricsAndQualityFlags(run, result, snapshotByKey);

        // Steps 6-8: candidates, then reasons and scenarios.
        writeCandidates(result, metricByKey);

        // Step 9: only the writer transitions the run to COMPLETED, after every flush above succeeded.
        run.markCompleted();
        analysisRunRepository.saveAndFlush(run);
    }

    private void validateResultMatchesRun(SpAnalysisRun run, Mvp2CalculationResult result) {
        if (!run.getAnalysisDate().equals(result.analysisDate())) {
            throw violation("result.analysisDate (" + result.analysisDate() + ") does not match run "
                    + run.getAnalysisRunId() + "'s analysisDate (" + run.getAnalysisDate() + ").");
        }
        if (!run.getInputSnapshotVersion().equals(result.inputSnapshotVersion())) {
            throw violation("result.inputSnapshotVersion (" + result.inputSnapshotVersion() + ") does not match run "
                    + run.getAnalysisRunId() + "'s inputSnapshotVersion (" + run.getInputSnapshotVersion() + ").");
        }

        Set<Mvp2StoreSkuKey> metricKeys = new HashSet<>();
        Map<Mvp2StoreSkuKey, Mvp2MetricResult> flatMetricByKey = new LinkedHashMap<>();
        for (Mvp2MetricResult metric : result.metrics()) {
            if (!run.getRuleVersion().equals(metric.calculationVersion())) {
                throw violation("Metric " + metric.storeId() + "/" + metric.skuId() + "'s calculationVersion ("
                        + metric.calculationVersion() + ") does not match run's ruleVersion (" + run.getRuleVersion() + ").");
            }
            Mvp2StoreSkuKey key = new Mvp2StoreSkuKey(metric.storeId(), metric.skuId());
            if (!metricKeys.add(key)) {
                throw violation("Duplicate metric store-SKU key " + key + " in result.metrics().");
            }
            flatMetricByKey.put(key, metric);
        }
        if (!metricKeys.equals(result.metricsByStoreSku().keySet())) {
            throw violation("result.metrics() and result.metricsByStoreSku() do not represent the same store-SKU set.");
        }
        for (Map.Entry<Mvp2StoreSkuKey, Mvp2MetricResult> entry : result.metricsByStoreSku().entrySet()) {
            Mvp2MetricResult indexedMetric = entry.getValue();
            Mvp2StoreSkuKey valueKey = new Mvp2StoreSkuKey(indexedMetric.storeId(), indexedMetric.skuId());
            if (!entry.getKey().equals(valueKey) || !indexedMetric.equals(flatMetricByKey.get(entry.getKey()))) {
                throw violation("result.metricsByStoreSku() contains a key-value association that disagrees with "
                        + "result.metrics(): key=" + entry.getKey() + ".");
            }
        }

        Map<Mvp2CandidateResult, Integer> flatCandidateCounts = multisetCount(result.candidates());
        List<Mvp2CandidateResult> indexedCandidates = new ArrayList<>();
        for (Map.Entry<Mvp2StoreSkuKey, List<Mvp2CandidateResult>> entry
                : result.candidatesByReceiver().entrySet()) {
            for (Mvp2CandidateResult candidate : entry.getValue()) {
                Mvp2StoreSkuKey receiverKey = new Mvp2StoreSkuKey(candidate.receiverStoreId(), candidate.skuId());
                if (!entry.getKey().equals(receiverKey)) {
                    throw violation("result.candidatesByReceiver() contains candidate "
                            + candidate.receiverStoreId() + "<-" + candidate.donorStoreId() + "/" + candidate.skuId()
                            + " under the wrong receiver key " + entry.getKey() + ".");
                }
                indexedCandidates.add(candidate);
            }
        }
        Map<Mvp2CandidateResult, Integer> indexedCandidateCounts = multisetCount(indexedCandidates);
        if (!flatCandidateCounts.equals(indexedCandidateCounts)) {
            throw violation("result.candidates() and result.candidatesByReceiver() do not represent the same candidate set.");
        }
    }

    private static Map<Mvp2CandidateResult, Integer> multisetCount(List<Mvp2CandidateResult> candidates) {
        Map<Mvp2CandidateResult, Integer> counts = new HashMap<>();
        for (Mvp2CandidateResult candidate : candidates) {
            counts.merge(candidate, 1, Integer::sum);
        }
        return counts;
    }

    private Map<Mvp2StoreSkuKey, SpInventorySnapshot> loadSnapshotMap(SpAnalysisRun run, Mvp2CalculationResult result) {
        List<SpInventorySnapshot> snapshots = snapshotRepository
                .findBySnapshotDateAndInputSnapshotVersionOrderByStoreIdAscSkuIdAsc(
                        run.getAnalysisDate(), run.getInputSnapshotVersion());
        Map<Mvp2StoreSkuKey, SpInventorySnapshot> byKey = new LinkedHashMap<>();
        for (SpInventorySnapshot snapshot : snapshots) {
            // uq_sp_inv_snapshot already guarantees at most one row per (date, store, sku, version);
            // snapshots outside the result's own metric keys are simply never looked up below.
            byKey.put(new Mvp2StoreSkuKey(snapshot.getStoreId(), snapshot.getSkuId()), snapshot);
        }
        for (Mvp2MetricResult metric : result.metrics()) {
            Mvp2StoreSkuKey key = new Mvp2StoreSkuKey(metric.storeId(), metric.skuId());
            if (!byKey.containsKey(key)) {
                throw violation("No SpInventorySnapshot found for " + key + " at analysisDate="
                        + run.getAnalysisDate() + ", inputSnapshotVersion=" + run.getInputSnapshotVersion() + ".");
            }
        }
        return byKey;
    }

    private Map<Mvp2StoreSkuKey, SpInventoryMetric> writeMetricsAndQualityFlags(
            SpAnalysisRun run, Mvp2CalculationResult result, Map<Mvp2StoreSkuKey, SpInventorySnapshot> snapshotByKey) {
        List<SpInventoryMetric> metricEntities = new ArrayList<>();
        Map<Mvp2StoreSkuKey, SpInventoryMetric> metricByKey = new LinkedHashMap<>();
        for (Mvp2MetricResult metric : result.metrics()) {
            Mvp2StoreSkuKey key = new Mvp2StoreSkuKey(metric.storeId(), metric.skuId());
            SpInventoryMetric entity = new SpInventoryMetric(
                    run, snapshotByKey.get(key), metric.stats(), metric.signal(), metric.rates(), metric.projection(),
                    metric.exception(), metric.expectedShortageQuantity(), metric.calculationVersion());
            metricEntities.add(entity);
            metricByKey.put(key, entity);
        }
        metricRepository.saveAllAndFlush(metricEntities);

        List<SpMetricQualityFlag> flagEntities = new ArrayList<>();
        for (Mvp2MetricResult metric : result.metrics()) {
            SpInventoryMetric entity = metricByKey.get(new Mvp2StoreSkuKey(metric.storeId(), metric.skuId()));
            // metric.qualityFlags() is an EnumSet, so this iterates in MetricQualityFlag's own
            // declaration order regardless of detection order -- current-task.md section 4 step 5.
            for (MetricQualityFlag flag : metric.qualityFlags()) {
                flagEntities.add(new SpMetricQualityFlag(entity, flag));
            }
        }
        qualityFlagRepository.saveAllAndFlush(flagEntities);

        return metricByKey;
    }

    private void writeCandidates(Mvp2CalculationResult result, Map<Mvp2StoreSkuKey, SpInventoryMetric> metricByKey) {
        List<SpRebalanceRecommendation> recommendationEntities = new ArrayList<>();
        for (Mvp2CandidateResult candidate : result.candidates()) {
            if (candidate.receiverStoreId().equals(candidate.donorStoreId())) {
                throw violation("Candidate receiver and donor store must differ (both were "
                        + candidate.receiverStoreId() + ", sku=" + candidate.skuId() + ").");
            }
            SpInventoryMetric receiverMetric = metricByKey.get(new Mvp2StoreSkuKey(candidate.receiverStoreId(), candidate.skuId()));
            SpInventoryMetric donorMetric = metricByKey.get(new Mvp2StoreSkuKey(candidate.donorStoreId(), candidate.skuId()));
            if (receiverMetric == null || donorMetric == null) {
                throw violation("Candidate " + candidate.receiverStoreId() + "<-" + candidate.donorStoreId() + "/"
                        + candidate.skuId() + " references a receiver or donor metric outside this run's result.");
            }
            recommendationEntities.add(SpRebalanceRecommendation.createMvp2Candidate(
                    receiverMetric, donorMetric, candidate.routeId(), candidate.candidateStatus(), candidate.candidateVersion(),
                    candidate.recommendationMode(), candidate.receiverShortageQuantity(), candidate.donorTransferableQuantity(),
                    candidate.recommendedQuantity(), candidate.projectedReceiverAtArrival(), candidate.projectedDonorAtDispatch(),
                    candidate.receiverCapacityRemaining()));
        }
        recommendationRepository.saveAllAndFlush(recommendationEntities);

        List<SpCandidateReason> reasonEntities = new ArrayList<>();
        List<SpRebalanceScenario> scenarioEntities = new ArrayList<>();
        for (int i = 0; i < result.candidates().size(); i++) {
            Mvp2CandidateResult candidate = result.candidates().get(i);
            SpRebalanceRecommendation recommendation = recommendationEntities.get(i);

            int reasonOrder = 1;
            for (TransferCandidateRejectionReason reason : candidate.rejectionReasons()) {
                reasonEntities.add(new SpCandidateReason(recommendation, reason, reasonOrder++));
            }
            for (TransferScenarioResult scenario : candidate.scenarios()) {
                scenarioEntities.add(new SpRebalanceScenario(recommendation, scenario, candidate.packageMultiple()));
            }
        }
        candidateReasonRepository.saveAllAndFlush(reasonEntities);
        scenarioRepository.saveAllAndFlush(scenarioEntities);
    }

    private static Mvp2OutputContractViolationException violation(String message) {
        return new Mvp2OutputContractViolationException(message);
    }
}
