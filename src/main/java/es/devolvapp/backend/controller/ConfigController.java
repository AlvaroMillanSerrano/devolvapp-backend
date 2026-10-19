package es.devolvapp.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {
    private final JdbcTemplate jdbc;

    @GetMapping("/api/config/parametros")
    public Map<String, Object> parameters() {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.query("SELECT param_key, param_value FROM app_parameter",
                (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)))
                .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        Map<String, BigDecimal> co2 = new LinkedHashMap<>();
        jdbc.query("SELECT code, co2_kg_per_container FROM container_type ORDER BY code",
                (rs, row) -> Map.entry(rs.getString(1), rs.getBigDecimal(2)))
                .forEach(entry -> co2.put(entry.getKey(), entry.getValue()));
        return Map.of(
                "economia", Map.of("importePorEnvase", decimal(values, "importe_por_envase", "0.10"),
                        "porcentajeCiudadano", decimal(values, "porcentaje_ciudadano", "0.40"),
                        "porcentajeEmpresa", decimal(values, "porcentaje_empresa", "0.60"),
                        "provisional", Boolean.parseBoolean(values.getOrDefault("importe_provisional", "true"))),
                "pedidos", Map.of("maxBolsas", Integer.parseInt(values.getOrDefault("max_bolsas_pedido", "5"))),
                "impacto", Map.of("kgCo2PorEnvase", co2,
                        "kgCo2PorKmCoche", decimal(values, "kg_co2_por_km_coche", "0.12"),
                        "kgCo2PorArbolAnio", decimal(values, "kg_co2_por_arbol_anio", "21"))
        );
    }

    @GetMapping("/api/catalogo/tipos-envase")
    public List<Map<String, Object>> containerTypes() {
        return jdbc.query("""
                SELECT code, name, icon, max_bag_capacity FROM container_type ORDER BY code
                """, (rs, row) -> {
            Map<String, Object> type = new LinkedHashMap<>();
            type.put("codigo", rs.getString("code")); type.put("nombre", rs.getString("name"));
            type.put("icono", rs.getString("icon")); type.put("capacidadMaximaBolsa", rs.getShort("max_bag_capacity"));
            return type;
        });
    }

    @GetMapping("/api/catalogo/tipos-incidencia")
    public List<Map<String, Object>> incidentTypes() {
        return jdbc.query("SELECT code, name FROM incident_type ORDER BY code",
                (rs, row) -> Map.of("codigo", rs.getString("code"), "nombre", rs.getString("name")));
    }

    @GetMapping("/api/catalogo/codigos-postales")
    public List<Map<String, Object>> postalCodes() {
        return jdbc.query("""
                SELECT code, neighbourhood, lat, lng, (zone_id IS NOT NULL) AS con_service
                FROM zip_code ORDER BY code
                """, (rs, row) -> Map.of("cp", rs.getString("code").trim(), "barrio", rs.getString("neighbourhood"),
                "lat", rs.getBigDecimal("lat"), "lng", rs.getBigDecimal("lng"),
                "conServicio", rs.getBoolean("con_service")));
    }

    private static BigDecimal decimal(Map<String, String> values, String key, String fallback) {
        return new BigDecimal(values.getOrDefault(key, fallback));
    }
}
