package com.bapegg.stockpilot.api.error;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpErrorCatalogRepository extends JpaRepository<SpErrorCatalog, String> {
}
