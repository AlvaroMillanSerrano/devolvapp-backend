package es.devolvapp.backend.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class ImpactService {
    private final JdbcTemplate jdbc;
    public Map<String, Object> forCitizen(Long userId) { return impact("c.citizen_id", userId, true); }
    public Map<String, Object> forCollector(Long userId) { return impact("c.collector_id", userId, false); }
    public Map<String, Object> forAll() {
        Map<String, Object> result = impact(null, null, false);
        result.put("totalHistoricoKg", result.get("co2EvitadoKg"));
        return result;
    }
    private Map<String, Object> impact(String column, Long id, boolean includeZone) {
        String where = column == null ? "" : " AND " + column + " = ?";
        Object[] args = column == null ? new Object[0] : new Object[]{id};
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> totals = jdbc.queryForObject("""
                SELECT COALESCE(SUM(s.co2_kg),0) co2, COALESCE(SUM(s.valid_containers),0) containers,
                       COUNT(s.id) collections
                FROM settlement s JOIN collection c ON c.id=s.collection_id
                WHERE c.status='COMPLETADA'
                """ + where, (rs, row) -> Map.of("co2", rs.getBigDecimal("co2"),
                "containers", rs.getLong("containers"), "collections", rs.getLong("collections")), args);
        result.put("co2EvitadoKg", totals.get("co2"));
        result.put("envasesReciclados", totals.get("containers"));
        result.put("recogidas", totals.get("collections"));
        result.put("porTipo", jdbc.query("""
                SELECT bc.container_type_code tipo, SUM(bc.quantity) envases,
                       SUM(bc.quantity * ct.co2_kg_per_container) co2
                FROM bag_content bc JOIN bag b ON b.id=bc.bag_id
                JOIN collection c ON c.id=b.collection_id
                JOIN container_type ct ON ct.code=bc.container_type_code
                WHERE c.status='COMPLETADA'
                """ + where + " GROUP BY bc.container_type_code ORDER BY bc.container_type_code",
                (rs, row) -> Map.of("tipoEnvase", rs.getString("tipo"), "envases", rs.getLong("envases"),
                        "co2Kg", rs.getBigDecimal("co2")), args));
        result.put("porMes", jdbc.query("""
                SELECT to_char(date_trunc('month', c.completed_at), 'YYYY-MM') mes, SUM(s.co2_kg) co2
                FROM settlement s JOIN collection c ON c.id=s.collection_id
                WHERE c.status='COMPLETADA'
                """ + where + " GROUP BY date_trunc('month', c.completed_at) ORDER BY mes",
                (rs, row) -> Map.of("mes", rs.getString("mes"), "co2Kg", rs.getBigDecimal("co2")), args));
        if (includeZone) {
            result.put("zona", jdbc.query("""
                    SELECT z.name nombre, COALESCE(SUM(s.co2_kg),0) co2
                    FROM settlement s JOIN collection c ON c.id=s.collection_id
                    JOIN zone z ON z.id=c.zone_id
                    WHERE c.status='COMPLETADA' AND c.citizen_id=?
                    GROUP BY z.id,z.name ORDER BY co2 DESC LIMIT 1
                    """, rs -> rs.next() ? Map.of("nombre", rs.getString("nombre"),
                    "co2EvitadoKg", rs.getBigDecimal("co2")) : null, id));
        } else result.put("zona", null);
        return result;
    }
}
