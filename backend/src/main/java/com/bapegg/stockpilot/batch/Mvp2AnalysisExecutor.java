package com.bapegg.stockpilot.batch;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * The Batch-type-free application entry point for one MVP-2 analysis run, per current-task.md
 * section 2: claim &rarr; input load &rarr; pure calculate &rarr; atomic write, in sequence. No
 * Spring Batch {@code Job}/{@code Step} type appears here; the Batch Tasklet delegates to this
 * entry point without moving orchestration or transaction ownership into the Batch layer.
 */
@Service
public class Mvp2AnalysisExecutor {

    private final Mvp2RunLifecycleService lifecycleService;
    private final Mvp2InputAdapter inputAdapter;
    private final Mvp2AtomicOutputWriter outputWriter;

    public Mvp2AnalysisExecutor(
            Mvp2RunLifecycleService lifecycleService, Mvp2InputAdapter inputAdapter, Mvp2AtomicOutputWriter outputWriter) {
        this.lifecycleService = lifecycleService;
        this.inputAdapter = inputAdapter;
        this.outputWriter = outputWriter;
    }

    /**
     * Per current-task.md section 3: an {@code ALREADY_RUNNING} claim throws without reading input
     * or writing output; an {@code ALREADY_COMPLETED} claim returns the existing id without
     * calling the adapter, calculation or writer at all. A {@code RuntimeException} from the
     * adapter, calculation or writer (after a {@code STARTED} claim) is recorded via
     * {@link Mvp2RunLifecycleService#markFailed} -- in its own transaction, after the output
     * transaction has already ended -- and then rethrown; a failure while recording the failure
     * itself is preserved as a suppressed exception on the original one, never swallowed.
     */
    public Mvp2ExecutionOutcome execute(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {
        Mvp2RunClaim claim = lifecycleService.claim(analysisDate, inputSnapshotVersion, ruleVersion);
        if (claim.status() == Mvp2RunClaimStatus.ALREADY_RUNNING) {
            throw new Mvp2RunAlreadyRunningException("Run for (" + analysisDate + ", " + inputSnapshotVersion + ", "
                    + ruleVersion + ") is already RUNNING.");
        }
        if (claim.status() == Mvp2RunClaimStatus.ALREADY_COMPLETED) {
            return new Mvp2ExecutionOutcome(claim.runId(), true);
        }

        try {
            Mvp2InputGraph graph = inputAdapter.load(analysisDate, inputSnapshotVersion);
            Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);
            outputWriter.writeAndComplete(claim.runId(), result);
            return new Mvp2ExecutionOutcome(claim.runId(), false);
        } catch (RuntimeException failure) {
            try {
                lifecycleService.markFailed(claim.runId());
            } catch (RuntimeException markFailedFailure) {
                failure.addSuppressed(markFailedFailure);
            }
            throw failure;
        }
    }
}
