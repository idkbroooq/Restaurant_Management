package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.exception.TableNotFoundException;
import com.restaurant.model.DiningSession;
import com.restaurant.model.RestaurantTable;
import com.restaurant.repository.SessionRepository;
import com.restaurant.repository.TableRepository;
import com.restaurant.util.ApiMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * // OOP: Layers, Encapsulation
 * Table + dining-session resolution for both customers (QR token) and admin.
 */
@Service
public class TableService {

    private final TableRepository tableRepo;
    private final SessionRepository sessionRepo;

    public TableService(TableRepository tableRepo, SessionRepository sessionRepo) {
        this.tableRepo = tableRepo;
        this.sessionRepo = sessionRepo;
    }

    /** Resolves a scanned QR token to its table (+ the current active session, if any). */
    public Dtos.TableSessionResponse getTableByToken(String token) {
        RestaurantTable table = tableRepo.findByToken(token)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(token));
        DiningSession session = sessionRepo.findOpenSessionByTableId(table.getId()).orElse(null);
        return ApiMapper.toTableSession(table, session);
    }

    /** Admin list: every table with its current ACTIVE session (single JOIN query). */
    public List<Dtos.RestaurantTableDto> listTables() {
        return tableRepo.findAllWithSession();
    }

    public Dtos.RestaurantTableDto createTable(Dtos.CreateTableRequest req) {
        RestaurantTable table = new RestaurantTable(
                null, req.tableNumber(), generateQrToken(), req.capacity(),
                req.isActive() == null || req.isActive(), LocalDateTime.now());
        RestaurantTable saved = tableRepo.save(table);
        return ApiMapper.toTableDto(saved, null, null);
    }

    public Dtos.RestaurantTableDto updateTable(Long id, Dtos.CreateTableRequest req) {
        RestaurantTable existing = tableRepo.findById(id)
                .orElseThrow(() -> new TableNotFoundException(String.valueOf(id)));
        RestaurantTable updated = new RestaurantTable(
                existing.getId(), req.tableNumber(), existing.getQrToken(), req.capacity(),
                req.isActive() == null || req.isActive(), existing.getCreatedAt());
        tableRepo.update(updated);

        Optional<DiningSession> session = sessionRepo.findOpenSessionByTableId(id);
        return ApiMapper.toTableDto(updated,
                session.map(DiningSession::getId).orElse(null),
                session.map(s -> s.getStatus().name()).orElse(null));
    }

    /** QR configuration for table-tent printing (the admin UI renders the code client-side). */
    public Dtos.TableQrResponse getQrCode(Long id, String baseUrl) {
        RestaurantTable table = tableRepo.findById(id)
                .orElseThrow(() -> new TableNotFoundException(String.valueOf(id)));
        String menuUrl = baseUrl + "/customer/index.html?table=" + table.getQrToken();
        return new Dtos.TableQrResponse(table.getId(), table.getTableNumber(),
                table.getQrToken(), menuUrl, null);
    }

    /** Opaque, unguessable token embedded in the table's QR code. */
    private static String generateQrToken() {
        return "tok_tbl_" + UUID.randomUUID().toString().replace("-", "");
    }
}
