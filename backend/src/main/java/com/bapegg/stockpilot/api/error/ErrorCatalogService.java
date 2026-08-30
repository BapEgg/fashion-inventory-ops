package com.bapegg.stockpilot.api.error;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Resolves one stable error code to its {@link ErrorPresentation}, per current-task.md section 3's
 * closing paragraph: a missing or {@code active_flag='N'} row falls back to a fixed internal-error
 * presentation, and a catalog lookup failure (the DB itself is unreachable) falls back to a fixed
 * persistence-unavailable presentation -- never any other invented text, and never the raw
 * SQL/constraint/stack message.
 */
@Service
public class ErrorCatalogService {

    private final SpErrorCatalogRepository repository;

    public ErrorCatalogService(SpErrorCatalogRepository repository) {
        this.repository = repository;
    }

    public ErrorPresentation resolve(String errorCode) {
        try {
            return repository.findById(errorCode)
                    .filter(SpErrorCatalog::isActive)
                    .map(ErrorPresentation::from)
                    .orElseGet(ErrorPresentation::internalFallback);
        } catch (DataAccessException e) {
            return ErrorPresentation.persistenceUnavailableFallback();
        }
    }
}
