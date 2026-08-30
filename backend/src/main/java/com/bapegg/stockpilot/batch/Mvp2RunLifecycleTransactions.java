package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * The three separate {@code REQUIRES_NEW} transactions behind one run claim/fail, per
 * current-task.md sections 2-3. Each method is its own transaction so a caller
 * ({@link Mvp2RunLifecycleService}) can catch a failure from one (e.g. the unique-constraint
 * violation from a concurrent insert race in {@link #claim}) and still run a fresh, independent
 * transaction afterward ({@link #resolveCreateRace}) without reusing the failed one.
 */
@Service
public class Mvp2RunLifecycleTransactions {

    private final SpAnalysisRunRepository analysisRunRepository;

    public Mvp2RunLifecycleTransactions(SpAnalysisRunRepository analysisRunRepository) {
        this.analysisRunRepository = analysisRunRepository;
    }

    /**
     * Always attempts a fresh insert first, per current-task.md section 3: "행 없음: RUNNING run을
     * saveAndFlush, STARTED 반환. concurrent insert는 DB unique가 중재한다." A concurrent claim for the
     * same natural key surfaces here as {@code DataIntegrityViolationException} against
     * {@code uq_sp_analysis_run} -- {@link Mvp2RunLifecycleService} catches that and calls
     * {@link #resolveCreateRace} in a new transaction rather than retrying this one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mvp2RunClaim claim(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {
        SpAnalysisRun run = analysisRunRepository.saveAndFlush(
                new SpAnalysisRun(analysisDate, ruleVersion, inputSnapshotVersion));
        return new Mvp2RunClaim(run.getAnalysisRunId(), Mvp2RunClaimStatus.STARTED);
    }

    /**
     * Runs only after {@link #claim} failed on the unique constraint -- never inserts, only reads
     * the now-existing row (under the same write lock {@link #claim} would have needed) and
     * applies current-task.md section 3's state table. Throws {@link NoSuchElementException} if no
     * row is found after all (should not normally happen, since the insert failure implies a row
     * with this exact key already exists); the facade rethrows the original insert failure in
     * that case instead of this exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mvp2RunClaim resolveCreateRace(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {
        SpAnalysisRun run = analysisRunRepository.lockByNaturalKey(analysisDate, inputSnapshotVersion, ruleVersion)
                .orElseThrow(() -> new NoSuchElementException("No sp_analysis_run row found for natural key ("
                        + analysisDate + ", " + inputSnapshotVersion + ", " + ruleVersion
                        + ") after a create-race insert failure."));
        return switch (run.getRunStatus()) {
            case FAILED -> {
                run.restart();
                analysisRunRepository.saveAndFlush(run);
                yield new Mvp2RunClaim(run.getAnalysisRunId(), Mvp2RunClaimStatus.STARTED);
            }
            case RUNNING -> new Mvp2RunClaim(run.getAnalysisRunId(), Mvp2RunClaimStatus.ALREADY_RUNNING);
            case COMPLETED -> new Mvp2RunClaim(run.getAnalysisRunId(), Mvp2RunClaimStatus.ALREADY_COMPLETED);
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long runId) {
        SpAnalysisRun run = analysisRunRepository.lockById(runId)
                .orElseThrow(() -> new NoSuchElementException("No sp_analysis_run row found for id " + runId + "."));
        run.markFailed();
        analysisRunRepository.saveAndFlush(run);
    }
}
