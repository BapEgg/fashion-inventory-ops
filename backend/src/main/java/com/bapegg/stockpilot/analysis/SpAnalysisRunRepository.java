package com.bapegg.stockpilot.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SpAnalysisRunRepository extends JpaRepository<SpAnalysisRun, Long> {

    Optional<SpAnalysisRun> findByAnalysisDateAndRuleVersion(LocalDate analysisDate, String ruleVersion);
}
