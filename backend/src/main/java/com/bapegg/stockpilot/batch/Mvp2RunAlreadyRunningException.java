package com.bapegg.stockpilot.batch;

/**
 * Thrown by {@link Mvp2AnalysisExecutor#execute} when the claimed run's natural key is already
 * {@code RUNNING} -- per current-task.md section 3, the executor must not read input or write
 * output in this case.
 */
public class Mvp2RunAlreadyRunningException extends RuntimeException {

    public Mvp2RunAlreadyRunningException(String message) {
        super(message);
    }
}
