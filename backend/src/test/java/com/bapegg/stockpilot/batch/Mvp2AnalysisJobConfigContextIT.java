package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.InventoryAnalysisTasklet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.SimpleJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the two Job/Step configurations coexist correctly in one Spring context, per
 * current-task.md's Required tests item 3: both MVP-1/MVP-2 bean names resolve without ambiguity,
 * the real (unwrapped) {@link JobRepository} target is the JDBC-backed
 * {@link SimpleJobRepository} -- not the in-memory {@code ResourcelessJobRepository} a missing
 * {@code @EnableJdbcJobRepository} would silently fall back to -- and only the MVP-2 Step uses the
 * {@link ResourcelessTransactionManager}. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2AnalysisJobConfigContextIT {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("inventoryAnalysisJob")
    private Job inventoryAnalysisJob;

    @Autowired
    @Qualifier("mvp2AnalysisJob")
    private Job mvp2AnalysisJob;

    @Autowired
    @Qualifier("inventoryAnalysisStep")
    private Step inventoryAnalysisStep;

    @Autowired
    @Qualifier("mvp2AnalysisStep")
    private Step mvp2AnalysisStep;

    @Test
    void mvp1AndMvp2JobBeansCoexistUnderTheirOwnQualifiedNames() {
        assertEquals("inventoryAnalysisJob", inventoryAnalysisJob.getName());
        assertEquals("mvp2AnalysisJob", mvp2AnalysisJob.getName());
        assertNotSame(inventoryAnalysisJob, mvp2AnalysisJob);
    }

    @Test
    void theRealJobRepositoryTargetIsTheJdbcBackedSimpleJobRepository() {
        assertEquals(SimpleJobRepository.class, AopUtils.getTargetClass(jobRepository),
                "A missing @EnableJdbcJobRepository would silently fall back to an in-memory "
                        + "ResourcelessJobRepository that never persists to BATCH_* -- this must be the real one.");
    }

    @Test
    void onlyTheMvp2StepUsesTheResourcelessTransactionManager() {
        PlatformTransactionManager mvp2TransactionManager =
                (PlatformTransactionManager) ReflectionTestUtils.getField(mvp2AnalysisStep, "transactionManager");
        PlatformTransactionManager mvp1TransactionManager =
                (PlatformTransactionManager) ReflectionTestUtils.getField(inventoryAnalysisStep, "transactionManager");

        assertTrue(mvp2TransactionManager instanceof ResourcelessTransactionManager,
                "mvp2AnalysisStep must use ResourcelessTransactionManager per current-task.md section 3.");
        assertFalse(mvp1TransactionManager instanceof ResourcelessTransactionManager,
                "inventoryAnalysisStep must keep its own (JPA) transaction manager, unaffected by the new Step.");
    }

    @Test
    void mvp1TaskletIsStillWiredIndependentlyOfTheNewMvp2Tasklet() {
        Object mvp1Tasklet = ReflectionTestUtils.getField(inventoryAnalysisStep, "tasklet");
        Object mvp2Tasklet = ReflectionTestUtils.getField(mvp2AnalysisStep, "tasklet");
        assertTrue(mvp1Tasklet instanceof InventoryAnalysisTasklet);
        assertTrue(mvp2Tasklet instanceof Mvp2AnalysisTasklet);
    }
}
