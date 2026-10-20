package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * // OOP: Layers, Interface Abstraction
 * Thin read-only service exposing the analytics queries to the admin dashboard.
 * (No business rules beyond defaulting the optional date range.)
 */
@Service
public class ReportService {

    private final ReportRepository reportRepo;

    public ReportService(ReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    public List<Dtos.DailySalesRow> dailySales(LocalDate startDate, LocalDate endDate) {
        return reportRepo.dailySales(startDate, endDate);
    }

    public List<Dtos.TopItemRow> topItems(Integer limit) {
        int effective = (limit == null || limit < 1) ? 5 : Math.min(limit, 100);
        return reportRepo.topItems(effective);
    }

    public List<Dtos.BusiestTableRow> busiestTables() {
        return reportRepo.busiestTables();
    }

    public Dtos.AvgPrepTimeResponse avgPrepTime() {
        return reportRepo.avgPrepTime();
    }
}
