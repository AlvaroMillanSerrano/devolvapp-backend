package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.common.PageResponse;
import es.devolvapp.backend.dto.recolector.IncidenciaRequest;
import es.devolvapp.backend.dto.recolector.ResultadoBolsaRequest;
import es.devolvapp.backend.dto.recolector.SolicitudZonaRequest;
import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecolectorService {
    private final JdbcTemplate jdbc;
    private final CiudadanoService ciudadanoService;
    private final CodeGenerator codes;
    private final ImpactService impactService;
    private final ParameterService parameters;

    public Map<String, Object> assigned(Long collectorId) {
        List<Map<String, Object>> assignments = jdbc.query("""
                SELECT z.id zona_id,z.name zona,zdc.day_of_week dia
                FROM zone_day_collector zdc JOIN zone z ON z.id=zdc.zone_id
                WHERE zdc.collector_id=? ORDER BY z.name,zdc.day_of_week
                """, (rs, row) -> Map.of("zonaId", rs.getLong("zona_id"), "zona", rs.getString("zona"),
                "dia", rs.getInt("dia")), collectorId);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        assignments.forEach(item -> {
            String key = item.get("zonaId").toString();
            grouped.computeIfAbsent(key, unused -> {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("zona", Map.of("id", item.get("zonaId"), "nombre", item.get("zona")));
                group.put("dias", new ArrayList<Integer>());
                return group;
            });
            @SuppressWarnings("unchecked")
            List<Integer> days = (List<Integer>) grouped.get(key).get("dias");
            days.add((Integer) item.get("dia"));
        });
        List<Map<String, Object>> collections = jdbc.query("""
                SELECT c.id,c.code,c.status,c.collection_date,c.start_time,c.end_time,c.addr_street_type,
                       c.addr_street_name,c.addr_street_number,c.addr_floor,c.addr_letter,c.addr_zip_code,
                       c.addr_city,c.addr_lat,c.addr_lng,z.id zone_id,z.name zone,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) num_bags,
                       COALESCE((SELECT SUM(quantity) FROM bag b WHERE b.collection_id=c.id),0) total_containers
                FROM collection c JOIN zone z ON z.id=c.zone_id
                WHERE c.collector_id=? AND (c.collection_date >= current_date OR c.status='EN_CAMINO')
                  AND c.status <> 'CANCELADA'
                ORDER BY c.collection_date,c.start_time,c.id
                """, (rs, row) -> collectionMap(rs), collectorId);
        return Map.of("asignaciones", List.copyOf(grouped.values()), "recogidas", collections);
    }

    public Map<String, Object> detail(Long collectorId, Long id) {
        Map<String, Object> order = new LinkedHashMap<>(requireAssigned(collectorId, id, null));
        order.putAll(ciudadanoService.getOrderAny(id));
        String citizenName = jdbc.queryForObject("""
                SELECT first_name || ' ' || last_name FROM d_user u
                WHERE u.id=(SELECT citizen_id FROM collection WHERE id=?)
                """, String.class, id);
        String phone = jdbc.queryForObject("""
                SELECT phone_number FROM d_user WHERE id=(SELECT citizen_id FROM collection WHERE id=?)
                """, String.class, id);
        order.put("ciudadano", Map.of("nombre", citizenName, "telefono", phone == null ? "" : phone));
        return order;
    }

    @Transactional
    public Map<String, Object> start(Long collectorId, Long id) {
        Map<String, Object> collection = requireAssigned(collectorId, id, "PENDIENTE");
        jdbc.update("UPDATE collection SET status='EN_CAMINO',started_at=now() WHERE id=?", id);
        return detail(collectorId, id);
    }

    @Transactional
    public Map<String, Object> recordBagResult(Long collectorId, Long collectionId, Long bagId,
                                                ResultadoBolsaRequest request) {
        Map<String, Object> collection = requireAssigned(collectorId, collectionId, "EN_CAMINO");
        List<Map<String, Object>> matches = jdbc.query("""
                SELECT quantity FROM bag WHERE id=? AND collection_id=?
                """, (rs, row) -> Map.of("quantity", rs.getInt(1)), bagId, collectionId);
        if (matches.isEmpty()) throw ApiException.notFound("La bolsa no pertenece a la recogida");
        int quantity = (int) matches.getFirst().get("quantity");
        int valid;
        switch (request.resultado()) {
            case ACEPTADA -> valid = quantity;
            case NO_ENCONTRADA -> valid = 0;
            case DISCREPANCIA -> {
                if (request.envasesValidos() == null)
                    throw ApiException.fieldError("VALIDATION_ERROR", "Indica los envases válidos",
                            "envasesValidos", "Este campo es obligatorio si hay una discrepancia");
                if (request.envasesValidos() > quantity)
                    throw ApiException.fieldError("VALIDATION_ERROR", "Los envases válidos superan lo declarado",
                            "envasesValidos", "No puede superar la cantidad declarada");
                valid = request.envasesValidos();
            }
            default -> throw ApiException.badRequest("VALIDATION_ERROR", "Resultado no válido");
        }
        jdbc.update("UPDATE bag SET result=?,valid_containers=?,note=? WHERE id=?",
                request.resultado().name(), valid, request.nota(), bagId);
        if (request.resultado() == ResultadoBolsaRequest.Resultado.DISCREPANCIA
                || request.resultado() == ResultadoBolsaRequest.Resultado.NO_ENCONTRADA) {
            String type = request.resultado() == ResultadoBolsaRequest.Resultado.DISCREPANCIA
                    ? "DISCREPANCIA" : "BOLSA_NO_ENCONTRADA";
            createIncident(collectionId, bagId, type,
                    request.nota() == null || request.nota().isBlank()
                            ? "Incidencia generada al verificar la bolsa " + bagId : request.nota(), collectorId);
        }
        return bag(bagId);
    }

    @Transactional
    public Map<String, Object> complete(Long collectorId, Long id) {
        requireAssigned(collectorId, id, "EN_CAMINO");
        Long unverified = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bag WHERE collection_id=? AND result IS NULL
                """, Long.class, id);
        if (unverified != null && unverified > 0)
            throw ApiException.conflict("BOLSAS_SIN_VERIFICAR", "Verifica todas las bolsas antes de completar");
        List<Map<String, Object>> values = jdbc.query("""
                SELECT COALESCE(SUM(valid_containers),0) valid,
                       COALESCE(SUM(quantity),0) declared,
                       COALESCE(SUM((SELECT SUM(bc.quantity*ct.co2_kg_per_container)
                                     FROM bag_content bc JOIN container_type ct
                                     ON ct.code=bc.container_type_code WHERE bc.bag_id=b.id)),0) declared_co2,
                       COALESCE(SUM(quantity),0) co2_denominator
                FROM bag b WHERE collection_id=?
                """, (rs, row) -> Map.of("valid", rs.getInt("valid"), "declared", rs.getInt("declared"),
                "co2", rs.getBigDecimal("declared_co2"), "denominator", rs.getInt("co2_denominator")), id);
        Map<String, Object> amounts = values.getFirst();
        int valid = (int) amounts.get("valid");
        var price = parameters.decimal("importe_por_envase", "0.10");
        var companyRate = parameters.decimal("porcentaje_empresa", "0.60");
        var total = price.multiply(java.math.BigDecimal.valueOf(valid)).setScale(2, java.math.RoundingMode.HALF_UP);
        var company = total.multiply(companyRate).setScale(2, java.math.RoundingMode.HALF_UP);
        var citizen = total.subtract(company);
        var declaredCo2 = (java.math.BigDecimal) amounts.get("co2");
        int denominator = (int) amounts.get("denominator");
        var co2 = denominator == 0 ? java.math.BigDecimal.ZERO
                : declaredCo2.multiply(java.math.BigDecimal.valueOf(valid))
                    .divide(java.math.BigDecimal.valueOf(denominator), 3, java.math.RoundingMode.HALF_UP);
        jdbc.update("UPDATE collection SET status='COMPLETADA',completed_at=now() WHERE id=?", id);
        jdbc.update("""
                INSERT INTO settlement(collection_id,valid_containers,total_amount,citizen_share,company_share,co2_kg)
                VALUES (?,?,?,?,?,?)
                """, id, valid, total, citizen, company, co2);
        return detail(collectorId, id);
    }

    @Transactional
    public Map<String, Object> reportIncident(Long collectorId, Long collectionId, IncidenciaRequest request) {
        requireAssigned(collectorId, collectionId, null);
        Long incidentId = createIncident(collectionId, null, request.tipo(), request.descripcion(), collectorId);
        return jdbc.queryForObject("SELECT id,code,created_at FROM incident WHERE id=?", (rs, row) ->
                Map.of("id", rs.getLong("id"), "codigo", rs.getString("code"), "creadaEn", rs.getObject("created_at")),
                incidentId);
    }

    public Map<String, Object> route(Long collectorId, LocalDate date, Double lat, Double lng) {
        if (date == null) throw ApiException.fieldError("VALIDATION_ERROR", "La fecha es obligatoria",
                "fecha", "Indica una fecha");
        Map<String, Object> origin = origin(lat, lng);
        List<Map<String, Object>> all = jdbc.query("""
                SELECT c.id,c.code,c.status,c.collection_date,c.start_time,c.end_time,c.addr_street_type,
                       c.addr_street_name,c.addr_street_number,c.addr_floor,c.addr_letter,c.addr_zip_code,
                       c.addr_city,c.addr_lat,c.addr_lng,z.id zone_id,z.name zone,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) num_bags,
                       COALESCE((SELECT SUM(quantity) FROM bag b WHERE b.collection_id=c.id),0) total_containers
                FROM collection c JOIN zone z ON z.id=c.zone_id
                WHERE c.collector_id=? AND c.collection_date=? ORDER BY c.start_time,c.id
                """, (rs, row) -> collectionMap(rs), collectorId, date);
        long done = all.stream().filter(row -> "COMPLETADA".equals(row.get("estado"))).count();
        long cancelled = all.stream().filter(row -> "CANCELADA".equals(row.get("estado"))).count();
        List<Map<String, Object>> remaining = new ArrayList<>(all.stream()
                .filter(row -> !"CANCELADA".equals(row.get("estado")) && !"COMPLETADA".equals(row.get("estado")))
                .toList());
        List<Map<String, Object>> stops = new ArrayList<>();
        double currentLat = ((Number) origin.get("lat")).doubleValue();
        double currentLng = ((Number) origin.get("lng")).doubleValue();
        double distance = 0;
        int order = 1;
        while (!remaining.isEmpty()) {
            double fromLat = currentLat, fromLng = currentLng;
            Map<String, Object> nearest = remaining.stream().min(Comparator.comparingDouble(
                    item -> haversine(fromLat, fromLng, number(item.get("lat")), number(item.get("lng"))))).orElseThrow();
            double leg = haversine(currentLat, currentLng, number(nearest.get("lat")), number(nearest.get("lng")));
            distance += leg;
            Map<String, Object> stop = new LinkedHashMap<>(nearest);
            stop.put("orden", order++);
            stop.put("distanciaDesdeAnteriorKm", Math.round(leg * 100.0) / 100.0);
            stops.add(stop);
            currentLat = number(nearest.get("lat"));
            currentLng = number(nearest.get("lng"));
            remaining.remove(nearest);
        }
        double speed = Double.parseDouble(parameters.get("velocidad_media_kmh", "25"));
        int stopMinutes = parameters.integer("minutos_por_parada", 10);
        int duration = (int) Math.ceil(distance / Math.max(1, speed) * 60 + stops.size() * stopMinutes);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fecha", date); response.put("origen", origin); response.put("paradas", stops);
        response.put("hechas", done); response.put("canceladas", cancelled);
        response.put("distanciaKm", Math.round(distance * 100.0) / 100.0); response.put("duracionMin", duration);
        return response;
    }

    public PageResponse<Map<String, Object>> history(Long collectorId, int page, int size) {
        int p = Math.max(0, page), s = Math.min(100, Math.max(1, size));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM collection WHERE collector_id=? AND status='COMPLETADA'",
                Long.class, collectorId);
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT c.id,c.code,c.status,c.created_at,c.collection_date,c.start_time,c.end_time,
                       c.completed_at,s.valid_containers,s.co2_kg,
                       EXISTS(SELECT 1 FROM incident i WHERE i.collection_id=c.id) has_incidents,
                       (SELECT COUNT(*) FROM bag b WHERE b.collection_id=c.id) num_bags,
                       COALESCE((SELECT SUM(quantity) FROM bag b WHERE b.collection_id=c.id),0) total_containers
                FROM collection c LEFT JOIN settlement s ON s.collection_id=c.id
                WHERE c.collector_id=? AND c.status='COMPLETADA'
                ORDER BY c.completed_at DESC LIMIT ? OFFSET ?
                """, (rs, row) -> {
            Map<String, Object> item = summaryMap(rs);
            item.put("completadoEn", rs.getObject("completed_at"));
            item.put("envasesValidos", rs.getInt("valid_containers"));
            item.put("co2EvitadoKg", rs.getBigDecimal("co2_kg"));
            item.put("tieneIncidencias", rs.getBoolean("has_incidents"));
            return item;
        }, collectorId, s, p * s);
        return new PageResponse<>(rows, total == null ? 0 : total,
                (int) Math.ceil((total == null ? 0 : total) / (double) s), p, s);
    }

    public Map<String, Object> preferences(Long collectorId) {
        List<Map<String, Object>> pref = jdbc.query("""
                SELECT u.preferred_zone_id,z.name FROM d_user u
                LEFT JOIN zone z ON z.id=u.preferred_zone_id WHERE u.id=?
                """, (rs, row) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Object id = rs.getObject(1);
            map.put("zonaPreferida", id == null ? null : Map.of("id", ((Number) id).longValue(), "nombre", rs.getString(2)));
            return map;
        }, collectorId);
        Map<String, Object> response = new LinkedHashMap<>(pref.getFirst());
        response.put("asignaciones", jdbc.query("""
                SELECT z.id,z.name,zdc.day_of_week FROM zone_day_collector zdc JOIN zone z ON z.id=zdc.zone_id
                WHERE zdc.collector_id=? ORDER BY z.name,zdc.day_of_week
                """, (rs, row) -> Map.of("zona", Map.of("id", rs.getLong("id"), "nombre", rs.getString("name")),
                "dias", List.of(rs.getInt("day_of_week"))), collectorId));
        response.put("zonas", jdbc.query("""
                SELECT z.id,z.name FROM zone z WHERE z.active=true ORDER BY z.name
                """, (rs, row) -> Map.of("id", rs.getLong(1), "nombre", rs.getString(2),
                "codigosPostales", jdbc.query("SELECT RTRIM(code) FROM zip_code WHERE zone_id=? ORDER BY code",
                        (postalRs, index) -> postalRs.getString(1), rs.getLong(1)))));
        response.put("solicitudes", jdbc.query("""
                SELECT r.id,r.status,r.created_at,r.reason,r.resolved_at,r.response,
                       r.current_zone_id,old.name current_zone,r.requested_zone_id,new.name requested_zone
                FROM zone_change_request r LEFT JOIN zone old ON old.id=r.current_zone_id
                JOIN zone new ON new.id=r.requested_zone_id WHERE r.collector_id=? ORDER BY r.created_at DESC
                """, (rs, row) -> zoneRequestMap(rs), collectorId));
        return response;
    }

    @Transactional
    public Map<String, Object> choosePreferredZone(Long collectorId, Long zoneId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM zone WHERE id=? AND active=true", Integer.class, zoneId);
        if (count == null || count == 0) throw ApiException.notFound("Zona no encontrada");
        Object current = jdbc.queryForObject("SELECT preferred_zone_id FROM d_user WHERE id=?", Object.class, collectorId);
        if (current != null) throw ApiException.conflict("ZONA_YA_ELEGIDA", "Ya has elegido una zona preferida");
        jdbc.update("UPDATE d_user SET preferred_zone_id=?,updated_at=now() WHERE id=?", zoneId, collectorId);
        return preferences(collectorId);
    }

    @Transactional
    public Map<String, Object> requestZoneChange(Long collectorId, SolicitudZonaRequest request) {
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM zone_change_request WHERE collector_id=? AND status='PENDIENTE'
                """, Integer.class, collectorId);
        if (pending != null && pending > 0)
            throw ApiException.conflict("SOLICITUD_PENDIENTE", "Ya tienes una solicitud pendiente");
        Integer zoneExists = jdbc.queryForObject("SELECT COUNT(*) FROM zone WHERE id=? AND active=true",
                Integer.class, request.zonaId());
        if (zoneExists == null || zoneExists == 0) throw ApiException.notFound("Zona solicitada no encontrada");
        Long id = jdbc.queryForObject("""
                INSERT INTO zone_change_request(collector_id,current_zone_id,requested_zone_id,reason)
                SELECT ?,preferred_zone_id,?,? FROM d_user WHERE id=? RETURNING id
                """, Long.class, collectorId, request.zonaId(), request.motivo(), collectorId);
        return zoneRequest(id);
    }

    @Transactional
    public Map<String, Object> cancelZoneRequest(Long collectorId, Long requestId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM zone_change_request WHERE id=? AND collector_id=? FOR UPDATE
                """, (rs, row) -> rs.getString(1), requestId, collectorId);
        if (statuses.isEmpty()) throw ApiException.notFound("Solicitud no encontrada");
        if (!"PENDIENTE".equals(statuses.getFirst()))
            throw ApiException.conflict("ESTADO_NO_CANCELABLE", "La solicitud ya está resuelta");
        jdbc.update("UPDATE zone_change_request SET status='CANCELADA',resolved_at=now() WHERE id=?", requestId);
        return zoneRequest(requestId);
    }

    private Map<String, Object> requireAssigned(Long collectorId, Long id, String status) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id,code,status FROM collection WHERE id=? AND collector_id=?
                """, (rs, row) -> Map.of("id", rs.getLong(1), "codigo", rs.getString(2), "estado", rs.getString(3)),
                id, collectorId);
        if (rows.isEmpty()) throw ApiException.forbidden("La recogida no está asignada a este recolector");
        if (status != null && !status.equals(rows.getFirst().get("estado")))
            throw ApiException.conflict("ESTADO_NO_PERMITIDO", "La operación no está permitida en el estado actual");
        return rows.getFirst();
    }

    private Map<String, Object> collectionMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>(summaryMap(rs));
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("tipoVia", rs.getString("addr_street_type")); address.put("nombreVia", rs.getString("addr_street_name"));
        address.put("numero", rs.getString("addr_street_number")); address.put("piso", rs.getString("addr_floor"));
        address.put("letra", rs.getString("addr_letter"));
        String postalCode = rs.getString("addr_zip_code");
        address.put("codigoPostal", postalCode == null ? null : postalCode.trim());
        address.put("ciudad", rs.getString("addr_city")); address.put("lat", rs.getObject("addr_lat"));
        address.put("lng", rs.getObject("addr_lng"));
        item.put("direccion", address); item.put("lat", rs.getObject("addr_lat")); item.put("lng", rs.getObject("addr_lng"));
        item.put("zona", Map.of("id", rs.getLong("zone_id"), "nombre", rs.getString("zone")));
        return item;
    }

    private Map<String, Object> summaryMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id")); item.put("codigo", rs.getString("code"));
        item.put("estado", rs.getString("status")); item.put("fecha", rs.getObject("collection_date"));
        item.put("horaInicio", rs.getObject("start_time")); item.put("horaFin", rs.getObject("end_time"));
        item.put("numBolsas", rs.getInt("num_bags")); item.put("totalEnvases", rs.getLong("total_containers"));
        return item;
    }

    private Map<String, Object> bag(Long id) {
        return jdbc.queryForObject("""
                SELECT id,code,quantity,max_capacity,result,valid_containers,note FROM bag WHERE id=?
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id")); item.put("codigo", rs.getString("code"));
            item.put("cantidad", rs.getShort("quantity")); item.put("capacidadMaxima", rs.getShort("max_capacity"));
            item.put("resultado", rs.getString("result")); item.put("envasesValidos", rs.getObject("valid_containers"));
            item.put("nota", rs.getString("note"));
            Map<String, Integer> contents = new LinkedHashMap<>();
            jdbc.query("SELECT container_type_code,quantity FROM bag_content WHERE bag_id=?",
                    (contentRs, rowIndex) -> Map.entry(contentRs.getString(1), contentRs.getInt(2)), id)
                    .forEach(entry -> contents.put(entry.getKey(), entry.getValue()));
            item.put("contenidos", contents);
            item.put("tipoEnvase", contents.size() == 1 ? contents.keySet().iterator().next() : "MEZCLADO");
            return item;
        }, id);
    }

    private Long createIncident(Long collectionId, Long bagId, String type, String description, Long collectorId) {
        Integer typeExists = jdbc.queryForObject("SELECT COUNT(*) FROM incident_type WHERE code=?", Integer.class, type);
        if (typeExists == null || typeExists == 0)
            throw ApiException.fieldError("VALIDATION_ERROR", "Tipo de incidencia desconocido", "tipo", "Selecciona un tipo válido");
        String code = codes.next("seq_incident_code", "INC-", 10, 4);
        Long id = jdbc.queryForObject("""
                INSERT INTO incident(code,collection_id,bag_id,type,description,created_by)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, code, collectionId, bagId, type, description, collectorId);
        jdbc.update("INSERT INTO incident_history(incident_id,author_id,action) VALUES (?,?,'Creada')", id, collectorId);
        return id;
    }

    private Map<String, Object> origin(Double lat, Double lng) {
        if (lat != null && lng != null) return Map.of("nombre", "Ubicación actual", "lat", lat, "lng", lng);
        Map<String, String> config = parameters.all();
        return Map.of("nombre", config.getOrDefault("nave_nombre", "Centro de operaciones"),
                "lat", Double.parseDouble(config.getOrDefault("nave_lat", "36.7213")),
                "lng", Double.parseDouble(config.getOrDefault("nave_lng", "-4.4214")));
    }

    private Map<String, Object> zoneRequest(Long id) {
        return jdbc.queryForObject("""
                SELECT r.id,r.status,r.created_at,r.reason,r.resolved_at,r.response,r.current_zone_id,
                       old.name current_zone,r.requested_zone_id,new.name requested_zone,
                       u.id collector_id,u.first_name,u.last_name
                FROM zone_change_request r JOIN d_user u ON u.id=r.collector_id
                LEFT JOIN zone old ON old.id=r.current_zone_id JOIN zone new ON new.id=r.requested_zone_id
                WHERE r.id=?
                """, (rs, row) -> zoneRequestMap(rs), id);
    }

    private Map<String, Object> zoneRequestMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", rs.getLong("id")); request.put("estado", rs.getString("status"));
        request.put("creadaEn", rs.getObject("created_at")); request.put("motivo", rs.getString("reason"));
        request.put("resueltaEn", rs.getObject("resolved_at")); request.put("respuesta", rs.getString("response"));
        Object currentId = rs.getObject("current_zone_id");
        request.put("zonaActual", currentId == null ? null
                : Map.of("id", ((Number) currentId).longValue(), "nombre", rs.getString("current_zone")));
        request.put("zonaSolicitada", Map.of("id", rs.getLong("requested_zone_id"),
                "nombre", rs.getString("requested_zone")));
        if (hasColumn(rs, "collector_id")) {
            request.put("recolector", Map.of("id", rs.getLong("collector_id"),
                    "nombre", rs.getString("first_name") + " " + rs.getString("last_name")));
        }
        return request;
    }

    private static boolean hasColumn(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        var columns = rs.getMetaData();
        for (int i = 1; i <= columns.getColumnCount(); i++)
            if (name.equalsIgnoreCase(columns.getColumnLabel(i))) return true;
        return false;
    }

    private static double number(Object value) { return value == null ? 0.0 : ((Number) value).doubleValue(); }
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
