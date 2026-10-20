package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Admin analytics backed by the SQL views + aggregates in db/advanced.sql (MANAGER only).
 */
@RestController
@RequestMapping("/api/admin/reports")
@Tag(name = "Admin - Reports", description = "Sales aggregations, velocity, and prep-time KPIs")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily-sales")
    @Operation(summary = "Daily sales summary report")
    public ResponseEntity<List<Dtos.DailySalesRow>> dailySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.dailySales(startDate, endDate));
    }

    @GetMapping("/top-items")
    @Operation(summary = "Top selling menu items")
    public ResponseEntity<List<Dtos.TopItemRow>> topItems(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(reportService.topItems(limit));
    }

    @GetMapping("/busiest-tables")
    @Operation(summary = "Busiest tables utilization report")
    public ResponseEntity<List<Dtos.BusiestTableRow>> busiestTables() {
        return ResponseEntity.ok(reportService.busiestTables());
    }

    @GetMapping("/avg-prep-time")
    @Operation(summary = "Kitchen average preparation time KPI")
    public ResponseEntity<Dtos.AvgPrepTimeResponse> avgPrepTime() {
        return ResponseEntity.ok(reportService.avgPrepTime());
    }
}
