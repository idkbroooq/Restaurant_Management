package com.restaurant.repository;

import com.restaurant.dto.Dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * // OOP: Interface, DAO Pattern
 * Read-only reporting queries backing the admin dashboard. Each method maps to
 * one of the analytical SQL queries in db/advanced.sql (views + aggregates).
 */
public interface ReportRepository {

    List<Dtos.DailySalesRow> dailySales(LocalDate startDate, LocalDate endDate);

    List<Dtos.TopItemRow> topItems(int limit);

    List<Dtos.BusiestTableRow> busiestTables();

    Dtos.AvgPrepTimeResponse avgPrepTime();
}
