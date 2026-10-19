package es.devolvapp.backend;

import es.devolvapp.backend.entity.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminHttpIntegrationTests extends PostgresIntegrationTest {
    @Test
    void adminDashboardIndicatorsAndPaginatedViewsExposeExpectedShapes() throws Exception {
        Actors actors = createActors();
        String admin = token(actors.adminId(), actors.adminEmail(), Role.ADMIN);
        var dashboard = send("GET", "/api/admin/pendientes", admin, null);
        var indicators = send("GET", "/api/admin/indicadores?desde=" + java.time.LocalDate.now().minusDays(1)
                + "&hasta=" + java.time.LocalDate.now(), admin, null);
        var collections = send("GET", "/api/admin/recogidas?page=0&size=10", admin, null);
        var withdrawals = send("GET", "/api/admin/transferencias?page=0&size=10", admin, null);
        var users = send("GET", "/api/admin/usuarios?page=0&size=10", admin, null);

        assertThat(dashboard.statusCode()).isEqualTo(200);
        assertThat(dashboard.body()).contains("transferencias", "incidenciasAbiertas", "diasSinRecolector");
        assertThat(indicators.statusCode()).isEqualTo(200);
        assertThat(indicators.body()).contains("recogidas", "economia", "impacto", "porZona");
        assertThat(collections.statusCode()).isEqualTo(200);
        assertThat(collections.body()).contains("content", "totalElements", "totalPages");
        assertThat(withdrawals.statusCode()).isEqualTo(200);
        assertThat(withdrawals.body()).contains("totales", "PENDIENTE", "COMPLETADA", "FALLIDA");
        assertThat(users.statusCode()).isEqualTo(200);
        assertThat(users.body()).contains("totalElements", "CIUDADANO", "RECOLECTOR", "ADMIN");
    }

    @Test
    void managesPostalZonesTimeSlotsAndCollectorAssignments() throws Exception {
        Actors actors = createActors();
        Long collectorId = createUser("free-collector-" + actors.suffix() + "@test.local", "RECOLECTOR", null);
        String admin = token(actors.adminId(), actors.adminEmail(), Role.ADMIN);
        int day = java.time.LocalDate.now().plusDays(5).getDayOfWeek().getValue();
        jdbc.update("INSERT INTO zip_code(code,neighbourhood,lat,lng) VALUES ('29991','North',36.75,-4.4)");
        jdbc.update("INSERT INTO zip_code(code,neighbourhood,lat,lng) VALUES ('29992','South',36.7,-4.4)");

        var created = send("POST", "/api/admin/zonas", admin,
                "{\"nombre\":\"North Zone\",\"codigosPostales\":[\"29991\"]}");
        assertThat(created.statusCode()).isEqualTo(201).withFailMessage(created.body());
        long zoneId = numberField(created.body(), "id");
        var slot = send("POST", "/api/admin/zonas/" + zoneId + "/franjas", admin,
                "{\"diaSemana\":%d,\"horaInicio\":\"09:00\",\"horaFin\":\"11:00\",\"capacidad\":8}".formatted(day));
        assertThat(slot.statusCode()).isEqualTo(201).withFailMessage(slot.body());
        long slotId = numberField(slot.body(), "id");
        assertThat(send("PUT", "/api/admin/zonas/" + zoneId + "/dias/" + day + "/recolector", admin,
                "{\"recolectorId\":%d}".formatted(collectorId)).statusCode()).isEqualTo(200);

        var secondZone = send("POST", "/api/admin/zonas", admin,
                "{\"nombre\":\"South Zone\",\"codigosPostales\":[\"29992\"]}");
        long secondZoneId = numberField(secondZone.body(), "id");
        var conflict = send("PUT", "/api/admin/zonas/" + secondZoneId + "/dias/" + day + "/recolector", admin,
                "{\"recolectorId\":%d}".formatted(collectorId));
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("RECOLECTOR_OCUPADO");

        assertThat(send("GET", "/api/admin/codigos-postales", admin, null).body()).contains("29991", "North Zone");
        assertThat(send("GET", "/api/admin/zonas", admin, null).body()).contains("North Zone", "29991", "09:00");
        assertThat(send("PUT", "/api/admin/franjas/" + slotId, admin,
                "{\"diaSemana\":%d,\"horaInicio\":\"10:00\",\"horaFin\":\"12:00\",\"capacidad\":9}".formatted(day)).statusCode()).isEqualTo(200);
        assertThat(send("DELETE", "/api/admin/franjas/" + slotId, admin, null).statusCode()).isEqualTo(204);
    }

    @Test
    void provisionsCollectorWithOneTimePasswordAndCanSuspendAccount() throws Exception {
        Actors actors = createActors();
        String admin = token(actors.adminId(), actors.adminEmail(), Role.ADMIN);
        var created = send("POST", "/api/admin/recolectores", admin,
                "{\"nombre\":\"Rita\",\"apellidos\":\"Recolectora\",\"email\":\"rita-%s@test.local\",\"telefono\":\"600123456\",\"dni\":\"12345678Z\"}"
                        .formatted(actors.suffix()));
        assertThat(created.statusCode()).isEqualTo(201).withFailMessage(created.body());
        assertThat(created.body()).contains("passwordProvisional");
        long collectorId = numberField(created.body(), "id");
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM d_user WHERE id=?", Boolean.class, collectorId)).isTrue();

        String collector = token(collectorId, "rita-" + actors.suffix() + "@test.local", Role.RECOLECTOR);
        var changed = send("PUT", "/api/perfil/password-inicial", collector, "{\"nueva\":\"Password-initial-987\"}");
        assertThat(changed.statusCode()).isEqualTo(200).withFailMessage(changed.body());
        assertThat(changed.body()).contains("\"debeCambiarPassword\":false");

        var suspended = send("POST", "/api/admin/usuarios/" + collectorId + "/suspender", admin, null);
        assertThat(suspended.statusCode()).isEqualTo(200).withFailMessage(suspended.body());
        assertThat(suspended.body()).contains("SUSPENDIDA");
        assertThat(send("GET", "/api/recolector/preferencias", collector, null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT status FROM d_user WHERE id=?", String.class, collectorId)).isEqualTo("SUSPENDIDA");
    }

    private static long numberField(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("JSON field not found: " + field + " in " + json);
        return Long.parseLong(matcher.group(1));
    }
}
