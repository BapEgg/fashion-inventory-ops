package com.bapegg.stockpilot.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The MVP-2 Batch Job/Step wiring, per current-task.md section 3. Relies entirely on the shared
 * JDBC {@code JobRepository} infrastructure {@link com.bapegg.stockpilot.analysis.InventoryAnalysisJobConfig}
 * already owns via {@code @EnableBatchProcessing}/{@code @EnableJdbcJobRepository} -- neither
 * annotation is repeated here.
 * <p>
 * The Step's transaction manager is deliberately a plain, non-bean
 * {@link ResourcelessTransactionManager}, not the JPA {@code PlatformTransactionManager}: the
 * already-accepted {@link Mvp2AnalysisExecutor} owns its own {@code REQUIRES_NEW} run-claim
 * transactions and single output transaction internally, and letting the Step's own long-lived
 * transaction join those (as the shared JPA transaction manager would) would silently widen those
 * already-verified transaction boundaries.
 */
@Configuration
public class Mvp2AnalysisJobConfig {

    @Bean
    public Job mvp2AnalysisJob(
            JobRepository jobRepository,
            Mvp2AnalysisJobParametersValidator mvp2AnalysisJobParametersValidator,
            @Qualifier("mvp2AnalysisStep") Step mvp2AnalysisStep) {
        return new JobBuilder("mvp2AnalysisJob", jobRepository)
                .validator(mvp2AnalysisJobParametersValidator)
                .start(mvp2AnalysisStep)
                .build();
    }

    @Bean
    public Step mvp2AnalysisStep(JobRepository jobRepository, Mvp2AnalysisTasklet mvp2AnalysisTasklet) {
        return new StepBuilder("mvp2AnalysisStep", jobRepository)
                .tasklet(mvp2AnalysisTasklet, new ResourcelessTransactionManager())
                .build();
    }
}
