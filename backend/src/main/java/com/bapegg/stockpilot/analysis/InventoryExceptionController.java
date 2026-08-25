package com.bapegg.stockpilot.analysis;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
public class InventoryExceptionController {

    private final InventoryExceptionService inventoryExceptionService;

    public InventoryExceptionController(InventoryExceptionService inventoryExceptionService) {
        this.inventoryExceptionService = inventoryExceptionService;
    }

    @GetMapping("/api/inventory-exceptions")
    public List<InventoryExceptionSummary> listExceptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate analysisDate) {
        return inventoryExceptionService.listExceptions(Optional.ofNullable(analysisDate));
    }

    @GetMapping("/api/inventory-exceptions/{id}")
    public InventoryExceptionDetail getException(@PathVariable Long id) {
        return inventoryExceptionService.getExceptionDetail(id);
    }
}
