package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * The single conversion/validation boundary between raw Spring Batch {@link JobParameters} and
 * {@link Mvp2AnalysisExecutor#execute}'s own three arguments, per current-task.md section 1.
 * Exactly three parameters, all identifying, no type coercion and no silent trim/default -- a
 * caller that gets any of this wrong is rejected immediately rather than producing a second
 * JobInstance shape for what should be the same natural key.
 */
public record Mvp2AnalysisJobParameters(LocalDate analysisDate, String inputSnapshotVersion, String ruleVersion) {

    private static final String ANALYSIS_DATE_KEY = "analysisDate";
    private static final String INPUT_SNAPSHOT_VERSION_KEY = "inputSnapshotVersion";
    private static final String RULE_VERSION_KEY = "ruleVersion";
    private static final Set<String> EXPECTED_KEYS =
            Set.of(ANALYSIS_DATE_KEY, INPUT_SNAPSHOT_VERSION_KEY, RULE_VERSION_KEY);
    private static final int MAX_INPUT_SNAPSHOT_VERSION_LENGTH = 64;
    private static final int MAX_RULE_VERSION_LENGTH = 32;

    public Mvp2AnalysisJobParameters {
        if (analysisDate == null) {
            throw new IllegalArgumentException("analysisDate must not be null.");
        }
        requireCleanValue(inputSnapshotVersion, INPUT_SNAPSHOT_VERSION_KEY, MAX_INPUT_SNAPSHOT_VERSION_LENGTH);
        requireCleanValue(ruleVersion, RULE_VERSION_KEY, MAX_RULE_VERSION_LENGTH);
        if (!DemandAnalysisRules.RULE_VERSION.equals(ruleVersion)) {
            throw new IllegalArgumentException(
                    "ruleVersion must equal " + DemandAnalysisRules.RULE_VERSION + " (was '" + ruleVersion + "').");
        }
    }

    private static void requireCleanValue(String value, String fieldName, int maxLength) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(fieldName + " must not have leading or trailing whitespace.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters.");
        }
    }

    /**
     * Validates metadata shape (exactly these three keys, no more, no less), each parameter's
     * type and {@code identifying} flag, and finally the values themselves -- all reported as
     * {@link InvalidJobParametersException}, never a raw {@link IllegalArgumentException} or
     * {@link ClassCastException}.
     */
    public static Mvp2AnalysisJobParameters from(JobParameters parameters) throws InvalidJobParametersException {
        Set<String> actualKeys = new HashSet<>();
        for (JobParameter<?> parameter : parameters.parameters()) {
            actualKeys.add(parameter.name());
        }
        if (!EXPECTED_KEYS.equals(actualKeys)) {
            throw new InvalidJobParametersException(
                    "Expected exactly the job parameters " + EXPECTED_KEYS + " but got " + actualKeys + ".");
        }

        LocalDate analysisDate = (LocalDate) shapeCheckedValue(parameters, ANALYSIS_DATE_KEY, LocalDate.class);
        String inputSnapshotVersion = (String) shapeCheckedValue(parameters, INPUT_SNAPSHOT_VERSION_KEY, String.class);
        String ruleVersion = (String) shapeCheckedValue(parameters, RULE_VERSION_KEY, String.class);

        try {
            return new Mvp2AnalysisJobParameters(analysisDate, inputSnapshotVersion, ruleVersion);
        } catch (IllegalArgumentException e) {
            throw new InvalidJobParametersException(e.getMessage());
        }
    }

    private static Object shapeCheckedValue(JobParameters parameters, String key, Class<?> expectedType)
            throws InvalidJobParametersException {
        JobParameter<?> parameter = parameters.getParameter(key);
        if (parameter.type() != expectedType) {
            throw new InvalidJobParametersException("Job parameter '" + key + "' must have type "
                    + expectedType.getName() + " (was " + parameter.type().getName() + ").");
        }
        if (!parameter.identifying()) {
            throw new InvalidJobParametersException("Job parameter '" + key + "' must be identifying.");
        }
        if (parameter.value() == null) {
            throw new InvalidJobParametersException("Job parameter '" + key + "' must not be null.");
        }
        return parameter.value();
    }

    /** All three parameters are identifying=true -- one execution must never produce two JobInstance shapes. */
    public JobParameters toJobParameters() {
        return new JobParametersBuilder()
                .addLocalDate(ANALYSIS_DATE_KEY, analysisDate, true)
                .addString(INPUT_SNAPSHOT_VERSION_KEY, inputSnapshotVersion, true)
                .addString(RULE_VERSION_KEY, ruleVersion, true)
                .toJobParameters();
    }
}
