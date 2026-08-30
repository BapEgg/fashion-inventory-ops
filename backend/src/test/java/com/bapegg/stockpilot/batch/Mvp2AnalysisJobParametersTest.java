package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link Mvp2AnalysisJobParameters}, per current-task.md's Required tests
 * item 1: exact key/type/identifying round-trip, plus every documented rejection.
 */
class Mvp2AnalysisJobParametersTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-GS-V1";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;

    @Test
    void toJobParametersRoundTripsWithExactKeysTypesAndIdentifyingFlags() throws InvalidJobParametersException {
        Mvp2AnalysisJobParameters params =
                new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION);

        JobParameters batchParams = params.toJobParameters();

        Set<String> names = new HashSet<>();
        for (JobParameter<?> parameter : batchParams.parameters()) {
            names.add(parameter.name());
            assertTrue(parameter.identifying(), "Every parameter must be identifying: " + parameter.name());
        }
        assertEquals(Set.of("analysisDate", "inputSnapshotVersion", "ruleVersion"), names);
        assertEquals(LocalDate.class, batchParams.getParameter("analysisDate").type());
        assertEquals(String.class, batchParams.getParameter("inputSnapshotVersion").type());
        assertEquals(String.class, batchParams.getParameter("ruleVersion").type());

        Mvp2AnalysisJobParameters roundTripped = Mvp2AnalysisJobParameters.from(batchParams);
        assertEquals(params, roundTripped);
    }

    @Test
    void fromRejectsAMissingParameter() {
        JobParameters missingRuleVersion = new JobParametersBuilder()
                .addLocalDate("analysisDate", ANALYSIS_DATE, true)
                .addString("inputSnapshotVersion", INPUT_SNAPSHOT_VERSION, true)
                .toJobParameters();

        assertThrows(InvalidJobParametersException.class, () -> Mvp2AnalysisJobParameters.from(missingRuleVersion));
    }

    @Test
    void fromRejectsAnExtraParameter() {
        JobParameters withExtra = new JobParametersBuilder()
                .addLocalDate("analysisDate", ANALYSIS_DATE, true)
                .addString("inputSnapshotVersion", INPUT_SNAPSHOT_VERSION, true)
                .addString("ruleVersion", RULE_VERSION, true)
                .addString("extra", "unexpected", true)
                .toJobParameters();

        assertThrows(InvalidJobParametersException.class, () -> Mvp2AnalysisJobParameters.from(withExtra));
    }

    @Test
    void fromRejectsAWrongTypeInsteadOfCoercing() {
        JobParameters stringDate = new JobParametersBuilder()
                .addString("analysisDate", ANALYSIS_DATE.toString(), true)
                .addString("inputSnapshotVersion", INPUT_SNAPSHOT_VERSION, true)
                .addString("ruleVersion", RULE_VERSION, true)
                .toJobParameters();

        assertThrows(InvalidJobParametersException.class, () -> Mvp2AnalysisJobParameters.from(stringDate));
    }

    @Test
    void fromRejectsANonIdentifyingParameter() {
        JobParameters nonIdentifying = new JobParametersBuilder()
                .addLocalDate("analysisDate", ANALYSIS_DATE, true)
                .addString("inputSnapshotVersion", INPUT_SNAPSHOT_VERSION, false)
                .addString("ruleVersion", RULE_VERSION, true)
                .toJobParameters();

        assertThrows(InvalidJobParametersException.class, () -> Mvp2AnalysisJobParameters.from(nonIdentifying));
    }

    @Test
    void constructorRejectsBlankInputSnapshotVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(ANALYSIS_DATE, "   ", RULE_VERSION));
    }

    @Test
    void constructorRejectsSurroundingWhitespaceWithoutSilentlyTrimming() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(ANALYSIS_DATE, " " + INPUT_SNAPSHOT_VERSION, RULE_VERSION));
    }

    @Test
    void constructorRejectsAnInputSnapshotVersionLongerThanSixtyFourCharacters() {
        String tooLong = "V".repeat(65);
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(ANALYSIS_DATE, tooLong, RULE_VERSION));
    }

    @Test
    void constructorRejectsARuleVersionLongerThanThirtyTwoCharacters() {
        // Longer than 32 chars AND still literally different from DemandAnalysisRules.RULE_VERSION,
        // so this proves the length check independently of the equality check below.
        String tooLong = "R".repeat(33);
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, tooLong));
    }

    @Test
    void constructorRejectsARuleVersionDifferentFromTheConfiguredOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, "MVP-1"));
    }

    @Test
    void constructorRejectsANullAnalysisDate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mvp2AnalysisJobParameters(null, INPUT_SNAPSHOT_VERSION, RULE_VERSION));
    }
}
