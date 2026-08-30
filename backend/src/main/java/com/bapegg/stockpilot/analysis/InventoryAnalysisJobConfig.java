package com.bapegg.stockpilot.analysis;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A single-tasklet Job so the whole analysis run commits or rolls back as one unit.
 * JobParameters (analysisDate, ruleVersion) give idempotency for free: Spring Batch's
 * JobRepository (the BATCH_* tables from V3) refuses to relaunch a Job whose JobInstance
 * already completed with the same parameters.
 * <p>
 * {@code @EnableBatchProcessing} plus {@code @EnableJdbcJobRepository} are both
 * required for that: {@code @EnableBatchProcessing} alone provides a
 * {@code ResourcelessJobRepository} — an in-memory, single-slot implementation that
 * never persists to the {@code BATCH_*} tables and only "remembers" a JobInstance
 * within the same JVM/ApplicationContext — and {@code @EnableJdbcJobRepository} is
 * what swaps that for the real, persistent, JDBC-backed one. A review found the gap
 * the hard way, by querying {@code BATCH_JOB_INSTANCE} in Oracle and finding it empty
 * despite the Job appearing to enforce idempotency in-process. {@code tablePrefix}
 * defaults to {@code "BATCH_"}, matching V3's schema exactly.
 */
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class InventoryAnalysisJobConfig {

    @Bean
    public Job inventoryAnalysisJob(
            JobRepository jobRepository, @Qualifier("inventoryAnalysisStep") Step inventoryAnalysisStep) {
        return new JobBuilder("inventoryAnalysisJob", jobRepository)
                .start(inventoryAnalysisStep)
                .build();
    }

    @Bean
    public Step inventoryAnalysisStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            InventoryAnalysisTasklet inventoryAnalysisTasklet) {
        return new StepBuilder("inventoryAnalysisStep", jobRepository)
                .tasklet(inventoryAnalysisTasklet, transactionManager)
                .build();
    }
}
