package com.bapegg.stockpilot.batch;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link Mvp2AnalysisExecutor}'s claim-driven control flow, per
 * current-task.md section 3's last bullet. Uses mocked {@link Mvp2RunLifecycleService}/
 * {@link Mvp2InputAdapter}/{@link Mvp2AtomicOutputWriter} collaborators -- no Spring context, no
 * Oracle -- since the behavior under test is which of those three the executor calls (and in what
 * order), not any of their own implementations.
 */
class Mvp2AnalysisExecutorTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 8, 28);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP2-EXEC-V1";
    private static final String RULE_VERSION = "MVP2-EXEC";

    private final Mvp2RunLifecycleService lifecycleService = mock(Mvp2RunLifecycleService.class);
    private final Mvp2InputAdapter inputAdapter = mock(Mvp2InputAdapter.class);
    private final Mvp2AtomicOutputWriter outputWriter = mock(Mvp2AtomicOutputWriter.class);
    private final Mvp2AnalysisExecutor executor = new Mvp2AnalysisExecutor(lifecycleService, inputAdapter, outputWriter);

    @Test
    void alreadyRunningClaimThrowsWithoutTouchingInputOrOutput() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(1L, Mvp2RunClaimStatus.ALREADY_RUNNING));

        assertThrows(Mvp2RunAlreadyRunningException.class,
                () -> executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION));

        verifyNoInteractions(inputAdapter, outputWriter);
    }

    @Test
    void alreadyCompletedClaimReturnsWithoutCallingAdapterCalculationOrWriter() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(7L, Mvp2RunClaimStatus.ALREADY_COMPLETED));

        Mvp2ExecutionOutcome outcome = executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION);

        assertEquals(7L, outcome.runId());
        assertTrue(outcome.alreadyCompleted());
        verifyNoInteractions(inputAdapter, outputWriter);
    }

    @Test
    void aStartedClaimRunsTheFullPipelineAndNeverMarksTheRunFailed() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(9L, Mvp2RunClaimStatus.STARTED));
        when(inputAdapter.load(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION)).thenReturn(emptyGraph());

        Mvp2ExecutionOutcome outcome = executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION);

        assertEquals(9L, outcome.runId());
        assertFalse(outcome.alreadyCompleted());
        verify(outputWriter, times(1)).writeAndComplete(eq(9L), any());
        verify(lifecycleService, never()).markFailed(any());
    }

    @Test
    void aWriterFailureAfterAStartedClaimMarksTheRunFailedAndRethrowsTheOriginalException() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(11L, Mvp2RunClaimStatus.STARTED));
        when(inputAdapter.load(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION)).thenReturn(emptyGraph());
        RuntimeException writerFailure = new Mvp2OutputContractViolationException("simulated writer failure");
        doThrow(writerFailure).when(outputWriter).writeAndComplete(eq(11L), any());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION));

        assertSame(writerFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        verify(lifecycleService, times(1)).markFailed(11L);
    }

    @Test
    void anAdapterFailureAfterAStartedClaimMarksTheRunFailedWithoutCallingTheWriter() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(12L, Mvp2RunClaimStatus.STARTED));
        RuntimeException adapterFailure = new InputContractViolationException("simulated adapter failure");
        when(inputAdapter.load(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION)).thenThrow(adapterFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION));

        assertSame(adapterFailure, thrown);
        verify(lifecycleService, times(1)).markFailed(12L);
        verifyNoInteractions(outputWriter);
    }

    @Test
    void aFailureWhileRecordingTheFailureIsPreservedAsASuppressedExceptionOnTheOriginal() {
        when(lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2RunClaim(13L, Mvp2RunClaimStatus.STARTED));
        RuntimeException adapterFailure = new InputContractViolationException("simulated adapter failure");
        when(inputAdapter.load(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION)).thenThrow(adapterFailure);
        RuntimeException markFailedFailure = new IllegalStateException("simulated markFailed failure");
        doThrow(markFailedFailure).when(lifecycleService).markFailed(13L);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION));

        assertSame(adapterFailure, thrown, "The original failure must still be the one rethrown, never the markFailed one.");
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(markFailedFailure, thrown.getSuppressed()[0]);
    }

    private static Mvp2InputGraph emptyGraph() {
        return new Mvp2InputGraph(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
