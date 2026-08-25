package com.bapegg.stockpilot.analysis;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A single-tasklet Job so the whole analysis run commits or rolls back as one unit.
 * JobParameters (analysisDate, ruleVersion) give idempotency for free: Spring Batch's
 * JobRepository (the BATCH_* tables from V3) refuses to relaunch a Job whose JobInstance
 * already completed with the same parameters.
 */
@Configuration
public class InventoryAnalysisJobConfig {

    @Bean
    public Job inventoryAnalysisJob(JobRepository jobRepository, Step inventoryAnalysisStep) {
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
