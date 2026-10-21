package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.ciudadano.RetiradaRequest;
import es.devolvapp.backend.exception.ApiException;
import es.devolvapp.backend.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CiudadanoService {
    private final JdbcTemplate jdbc;
    private final WalletService walletService;
    private final ImpactService impactService;
    private final CodeGenerator codeGenerator;

    public PageResponse<Map<String, Object>> listOrders(Long citizenId, String state, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String where = " WHERE citizen_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(citizenId);
        if (state != null && !state.isBlank()) {
            if ("ACTIVOS".equalsIgnoreCase(state)) {
                where += " AND status IN ('PENDIENTE','EN_CAMINO') ";
            } else {
                where += " AND status=? ";
                args.add(state.toUpperCase());
            }
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM collection" + where, Long.class, args.toArray());
        String sql = """
                SELECT c.id, c.code, c.status, c.created_at, c.collection_date, c.start_time, c.end_time,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) AS bag_count,
                       COALESCE((SELECT SUM(b.quantity) FROM bag b WHERE b.collection_id=c.id),0) AS total_containers,
                       (SELECT s.citizen_share FROM settlement s WHERE s.collection_id=c.id) AS citizen_share,
                       EXISTS(SELECT 1 FROM incident i WHERE i.collection_id=c.id) AS has_incidents
                FROM collection c
                """ + where.replace("citizen_id", "c.citizen_id").replace("status", "c.status")
                + " ORDER BY c.collection_date DESC NULLS LAST,c.created_at DESC,c.id DESC LIMIT ? OFFSET ?";
        args.add(safeSize);
        args.add(safePage * safeSize);
        List<Map<String, Object>> content = jdbc.query(sql, (rs, row) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", rs.getLong("id"));
            result.put("codigo", rs.getString("code"));
            result.put("estado", rs.getString("status"));
            result.put("creadoEn", rs.getObject("created_at"));
            result.put("fecha", rs.getObject("collection_date"));
            result.put("horaInicio", rs.getObject("start_time"));
            result.put("horaFin", rs.getObject("end_time"));
            result.put("numBolsas", rs.getInt("bag_count"));
            result.put("totalEnvases", rs.getLong("total_containers"));
            result.put("importeCiudadano", rs.getBigDecimal("citizen_share"));
            result.put("tieneIncidencias", rs.getBoolean("has_incidents"));
            return result;
        }, args.toArray());
        return new PageResponse<>(content, total == null ? 0 : total,
                (int) Math.ceil((total == null ? 0 : total) / (double) safeSize), safePage, safeSize);
    }

    public Map<String, Object> getOrder(Long citizenId, Long orderId) {
        return orderDetail(orderSummary(citizenId, orderId, false), orderId);
    }

    public Map<String, Object> getOrderAny(Long orderId) {
        return orderDetail(orderSummary(null, orderId, true), orderId);
    }

    private Map<String, Object> orderDetail(Map<String, Object> result, Long orderId) {
        result.put("direccion", addressSnapshot(orderId));
        result.put("iniciadoEn", result.remove("startedAt"));
        result.put("completadoEn", result.remove("completedAt"));
        result.put("canceladoEn", result.remove("cancelledAt"));
        result.put("cancelable", "PENDIENTE".equals(result.get("estado")));
        result.put("bolsas", bags(orderId));
        result.put("liquidacion", settlement(orderId));
        result.put("incidencias", incidents(orderId));
        return result;
    }

    @Transactional
    public Map<String, Object> cancelOrder(Long citizenId, Long orderId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM collection WHERE id=? AND citizen_id=? FOR UPDATE
                """, (rs, row) -> rs.getString(1), orderId, citizenId);
        if (statuses.isEmpty()) throw ApiException.notFound("Pedido no encontrado");
        if (!"PENDIENTE".equals(statuses.getFirst()))
            throw ApiException.conflict("ESTADO_NO_CANCELABLE", "El pedido ya no se puede cancelar");
        jdbc.update("""
                UPDATE collection SET status='CANCELADA', cancelled_at=now(), cancelled_by=?
                WHERE id=? AND citizen_id=?
                """, citizenId, orderId, citizenId);
        return getOrder(citizenId, orderId);
    }

    public Map<String, Object> availability(Long citizenId) {
        List<Map<String, Object>> locations = jdbc.query("""
                SELECT RTRIM(a.zip_code) cp, z.id zone_id, z.name zone_name
                FROM address a LEFT JOIN zip_code p ON p.code=a.zip_code
                LEFT JOIN zone z ON z.id=p.zone_id WHERE a.user_id=?
                """, (rs, row) -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("cp", rs.getString("cp"));
            rowData.put("zoneId", rs.getObject("zone_id"));
            rowData.put("zoneName", rs.getString("zone_name"));
            return rowData;
        }, citizenId);
        if (locations.isEmpty()) throw ApiException.conflict("ZONA_SIN_SERVICIO", "El ciudadano no tiene domicilio");
        Map<String, Object> location = locations.getFirst();
        Object zoneId = location.get("zoneId");
        if (zoneId == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("codigoPostal", location.get("cp"));
            response.put("zona", null);
            response.put("huecos", List.of());
            return response;
        }
        Number zoneNumber = (Number) zoneId;
        Map<String, Object> zone = Map.of("id", zoneNumber.longValue(), "nombre", location.get("zoneName"));
        LocalDateTime now = LocalDateTime.now();
        LocalDate start = now.toLocalDate();
        List<Map<String, Object>> slots = jdbc.query("""
                SELECT id, day_of_week, start_time, end_time, capacity
                FROM time_slot WHERE zone_id=? AND active=true ORDER BY day_of_week,start_time
                """, (rs, row) -> Map.of("id", rs.getLong("id"), "diaSemana", rs.getInt("day_of_week"),
                        "horaInicio", rs.getTime("start_time").toLocalTime(),
                        "horaFin", rs.getTime("end_time").toLocalTime(),
                        "capacidad", rs.getInt("capacity")), zoneNumber.longValue());
        List<Map<String, Object>> windows = new ArrayList<>();
        for (int day = 0; day < 14; day++) {
            LocalDate date = start.plusDays(day);
            for (Map<String, Object> slot : slots) {
                if (date.getDayOfWeek().getValue() != (Integer) slot.get("diaSemana")) continue;
                if (!CollectionTimeRules.hasNotEnded(date, (LocalTime) slot.get("horaFin"), now)) continue;
                Long slotId = (Long) slot.get("id");
                Integer capacity = (Integer) slot.get("capacidad");
                Long occupied = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM collection
                        WHERE time_slot_id=? AND collection_date=? AND status <> 'CANCELADA'
                        """, Long.class, slotId, date);
                long free = capacity - (occupied == null ? 0 : occupied);
                if (free <= 0) continue;
                windows.add(Map.of("fecha", date, "franjaId", slotId, "horaInicio", slot.get("horaInicio"),
                        "horaFin", slot.get("horaFin"), "plazasLibres", free, "completa", false));
            }
        }
        return Map.of("codigoPostal", location.get("cp"), "zona", zone, "huecos", windows);
    }

    public Map<String, Object> wallet(Long citizenId) {
        return Map.of("saldoDisponible", walletService.balance(citizenId));
    }

    public Map<String, Object> impact(Long citizenId) { return impactService.forCitizen(citizenId); }

    public PageResponse<Map<String, Object>> movements(Long citizenId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String base = """
                SELECT * FROM (
                    SELECT s.id, 'ABONO' tipo, s.created_at fecha, s.citizen_share importe,
                           'COMPLETADA' estado, 'Recogida ' || c.code concepto, NULL::text referencia,
                           NULL::text motivo_fallo, s.created_at procesada_en
                    FROM settlement s JOIN collection c ON c.id=s.collection_id WHERE c.citizen_id=?
                    UNION ALL
                    SELECT w.id, 'RETIRADA', w.requested_at, w.amount, w.status,
                           'Retirada ' || w.code, w.bank_reference, w.failure_reason, w.processed_at
                    FROM withdrawal w WHERE w.citizen_id=?
                ) movements
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + base + ") all_movements",
                Long.class, citizenId, citizenId);
        List<Map<String, Object>> content = jdbc.query(base + " ORDER BY fecha DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id")); item.put("tipo", rs.getString("tipo"));
                    item.put("fecha", rs.getObject("fecha")); item.put("importe", rs.getBigDecimal("importe"));
                    item.put("estado", rs.getString("estado")); item.put("concepto", rs.getString("concepto"));
                    item.put("referencia", rs.getString("referencia")); item.put("motivoFallo", rs.getString("motivo_fallo"));
                    item.put("procesadaEn", rs.getObject("procesada_en")); return item;
                }, citizenId, citizenId, safeSize, safePage * safeSize);
        return new PageResponse<>(content, total == null ? 0 : total,
                (int) Math.ceil((total == null ? 0 : total) / (double) safeSize), safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> requestWithdrawal(Long citizenId, RetiradaRequest request) {
        jdbc.queryForObject("SELECT id FROM d_user WHERE id=? FOR UPDATE", Long.class, citizenId);
        String iban = request.iban().replaceAll("\\s+", "").toUpperCase();
        if (!validIban(iban)) throw ApiException.fieldError("VALIDATION_ERROR", "El IBAN no es válido",
                "iban", "Introduce un IBAN válido");
        BigDecimal balance = walletService.balance(citizenId);
        if (request.importe().compareTo(balance) > 0)
            throw ApiException.unprocessable("SALDO_INSUFICIENTE", "El importe supera el saldo disponible");
        String code = codeGenerator.next("seq_withdrawal_code", "R-", 10, 3);
        Long id = jdbc.queryForObject("""
                INSERT INTO withdrawal (code,citizen_id,amount,iban)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class, code, citizenId, request.importe(), iban);
        jdbc.update("INSERT INTO withdrawal_history(withdrawal_id,status,author_id) VALUES (?, 'PENDIENTE', ?)",
                id, citizenId);
        return Map.of("id", id, "codigo", code, "estado", "PENDIENTE",
                "importe", request.importe(), "saldoDisponible", balance.subtract(request.importe()));
    }

    private Map<String, Object> orderSummary(Long citizenId, Long id, boolean allowAny) {
        String ownerFilter = allowAny ? "" : " AND c.citizen_id=?";
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT c.id,c.code,c.status,c.created_at,c.collection_date,c.start_time,c.end_time,
                       c.started_at,c.completed_at,c.cancelled_at,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) num_bags,
                       COALESCE((SELECT SUM(b.quantity) FROM bag b WHERE b.collection_id=c.id),0) total_containers
                FROM collection c WHERE c.id=?
                """ + ownerFilter, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id")); item.put("codigo", rs.getString("code"));
            item.put("estado", rs.getString("status")); item.put("creadoEn", rs.getObject("created_at"));
            item.put("fecha", rs.getObject("collection_date")); item.put("horaInicio", rs.getObject("start_time"));
            item.put("horaFin", rs.getObject("end_time")); item.put("startedAt", rs.getObject("started_at"));
            item.put("completedAt", rs.getObject("completed_at")); item.put("cancelledAt", rs.getObject("cancelled_at"));
            item.put("numBolsas", rs.getInt("num_bags")); item.put("totalEnvases", rs.getLong("total_containers"));
            return item;
        }, allowAny ? new Object[]{id} : new Object[]{id, citizenId});
        if (rows.isEmpty()) throw ApiException.notFound("Pedido no encontrado");
        return rows.getFirst();
    }

    private Map<String, Object> addressSnapshot(Long orderId) {
        return jdbc.query("""
                SELECT addr_street_type,addr_street_name,addr_street_number,addr_floor,addr_letter,
                       addr_zip_code,addr_city,addr_lat,addr_lng FROM collection WHERE id=?
                """, rs -> {
            if (!rs.next()) return Map.of();
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("tipoVia", rs.getString(1)); address.put("nombreVia", rs.getString(2));
            address.put("numero", rs.getString(3)); address.put("piso", rs.getString(4));
            address.put("letra", rs.getString(5));
            String postalCode = rs.getString(6);
            address.put("codigoPostal", postalCode == null ? null : postalCode.trim());
            address.put("ciudad", rs.getString(7)); address.put("lat", rs.getObject(8)); address.put("lng", rs.getObject(9));
            return address;
        }, orderId);
    }

    private List<Map<String, Object>> bags(Long orderId) {
        List<Long> ids = jdbc.query("SELECT id FROM bag WHERE collection_id=? ORDER BY id",
                (rs, row) -> rs.getLong(1), orderId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> bag = jdbc.queryForObject("""
                    SELECT id,code,quantity,max_capacity,result,valid_containers,note
                    FROM bag WHERE id=?
                    """, (rs, row) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getLong("id")); item.put("codigo", rs.getString("code"));
                item.put("cantidad", rs.getShort("quantity")); item.put("capacidadMaxima", rs.getShort("max_capacity"));
                item.put("resultado", rs.getString("result")); item.put("envasesValidos", rs.getObject("valid_containers"));
                item.put("nota", rs.getString("note")); return item;
            }, id);
            Map<String, Integer> contents = new LinkedHashMap<>();
            jdbc.query("SELECT container_type_code,quantity FROM bag_content WHERE bag_id=?",
                    (rs, row) -> Map.entry(rs.getString(1), rs.getInt(2)), id)
                    .forEach(entry -> contents.put(entry.getKey(), entry.getValue()));
            bag.put("contenidos", contents);
            bag.put("tipoEnvase", contents.size() == 1 ? contents.keySet().iterator().next() : "MEZCLADO");
            result.add(bag);
        }
        return result;
    }

    private Map<String, Object> settlement(Long orderId) {
        return jdbc.query("""
                SELECT valid_containers,citizen_share FROM settlement WHERE collection_id=?
                """, rs -> rs.next() ? Map.of("envasesValidos", rs.getInt(1),
                "importeCiudadano", rs.getBigDecimal(2)) : null, orderId);
    }

    private List<Map<String, Object>> incidents(Long orderId) {
        return jdbc.query("""
                SELECT i.id,i.code,i.type,i.status,i.created_at,i.resolution
                FROM incident i WHERE i.collection_id=? ORDER BY i.created_at
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id")); item.put("codigo", rs.getString("code"));
            item.put("tipo", rs.getString("type")); item.put("estado", rs.getString("status"));
            item.put("creadaEn", rs.getObject("created_at")); item.put("resolucion", rs.getString("resolution"));
            return item;
        }, orderId);
    }

    private static boolean validIban(String iban) {
        if (!iban.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char ch : rearranged.toCharArray()) {
            String digits = Character.isLetter(ch) ? Integer.toString(ch - 'A' + 10) : Character.toString(ch);
            for (char digit : digits.toCharArray()) remainder = (remainder * 10 + digit - '0') % 97;
        }
        return remainder == 1;
    }
}
