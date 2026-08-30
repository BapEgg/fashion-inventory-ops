package com.bapegg.stockpilot.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

/**
 * Delegates entirely to {@link Mvp2AnalysisJobParameters#from} so the parameter-shape/value rules
 * live in exactly one place -- this validator and {@link Mvp2AnalysisTasklet} must never diverge
 * on what a valid MVP-2 analysis JobParameters looks like.
 */
@Component
public class Mvp2AnalysisJobParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        Mvp2AnalysisJobParameters.from(parameters);
    }
}
