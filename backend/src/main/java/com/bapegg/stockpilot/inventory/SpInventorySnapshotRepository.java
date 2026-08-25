package com.bapegg.stockpilot.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SpInventorySnapshotRepository extends JpaRepository<SpInventorySnapshot, Long> {

    List<SpInventorySnapshot> findBySnapshotDate(LocalDate snapshotDate);
}
