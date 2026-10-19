package es.devolvapp.backend;

import es.devolvapp.backend.entity.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CitizenCollectorHttpIntegrationTests extends PostgresIntegrationTest {
    @Test
    void citizenCanCheckAvailabilityReadAndCancelOwnCollection() throws Exception {
        Actors actors = createActors();
        String citizen = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        String otherCitizen = token(createUser("other-" + actors.suffix() + "@test.local", "CIUDADANO", null),
                "other-" + actors.suffix() + "@test.local", Role.CIUDADANO);
        Long collectionId = createOrder(actors, citizen, 3);

        var list = send("GET", "/api/ciudadano/pedidos?page=0&size=10", citizen, null);
        var detail = send("GET", "/api/ciudadano/pedidos/" + collectionId, citizen, null);
        var deniedDetail = send("GET", "/api/ciudadano/pedidos/" + collectionId, otherCitizen, null);
        var availability = send("GET", "/api/ciudadano/disponibilidad", citizen, null);
        var cancelled = send("POST", "/api/ciudadano/pedidos/" + collectionId + "/cancelar", citizen, null);
        var duplicateCancel = send("POST", "/api/ciudadano/pedidos/" + collectionId + "/cancelar", citizen, null);

        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("totalElements", "totalEnvases");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("bolsas", "contenidos", "cancelable");
        assertThat(deniedDetail.statusCode()).isEqualTo(404);
        assertThat(availability.statusCode()).isEqualTo(200);
        assertThat(availability.body()).contains(actors.zip(), "huecos", "plazasLibres");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("CANCELADA");
        assertThat(duplicateCancel.statusCode()).isEqualTo(409);
    }

    @Test
    void collectorReadsAssignedRouteAndCanRequestThenCancelZoneChange() throws Exception {
        Actors actors = createActors();
        String collector = token(actors.collectorId(), actors.collectorEmail(), Role.RECOLECTOR);
        String admin = token(actors.adminId(), actors.adminEmail(), Role.ADMIN);
        Long collectionId = createOrder(actors, token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO), 3);
        int day = LocalDate.now().plusDays(5).getDayOfWeek().getValue();
        Long anotherZone = jdbc.queryForObject("INSERT INTO zone(name) VALUES ('Requested Zone') RETURNING id", Long.class);
        jdbc.update("INSERT INTO zip_code(code,neighbourhood,lat,lng,zone_id) VALUES ('29990','Other',36.73,-4.43,?)", anotherZone);

        var assigned = send("GET", "/api/recolector/recogidas", collector, null);
        var detail = send("GET", "/api/recolector/recogidas/" + collectionId, collector, null);
        var route = send("GET", "/api/recolector/ruta?fecha=" + LocalDate.now().plusDays(5), collector, null);
        var preferences = send("GET", "/api/recolector/preferencias", collector, null);
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(assigned.body()).contains("asignaciones", "recogidas", "PENDIENTE");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("ciudadano", "bolsas");
        assertThat(route.statusCode()).isEqualTo(200);
        assertThat(route.body()).contains("paradas", "distanciaKm", "duracionMin");
        assertThat(preferences.statusCode()).isEqualTo(200);
        assertThat(preferences.body()).contains("zonaPreferida", "Requested Zone");

        var requested = send("POST", "/api/recolector/solicitudes-zona", collector,
                "{\"zonaId\":" + anotherZone + ",\"motivo\":\"Cambio de domicilio\"}");
        assertThat(requested.statusCode()).isEqualTo(201).withFailMessage(requested.body());
        long requestId = numberField(requested.body(), "id");
        var duplicate = send("POST", "/api/recolector/solicitudes-zona", collector,
                "{\"zonaId\":" + anotherZone + ",\"motivo\":\"Otra solicitud\"}");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        var adminQueue = send("GET", "/api/admin/solicitudes-zona?estado=PENDIENTE", admin, null);
        assertThat(adminQueue.statusCode()).isEqualTo(200);
        assertThat(adminQueue.body()).contains("Cambio de domicilio");
        var resolved = send("POST", "/api/admin/solicitudes-zona/" + requestId + "/resolver", admin,
                "{\"aprobar\":true,\"moverDias\":false,\"respuesta\":\"Aprobada\"}");
        assertThat(resolved.statusCode()).isEqualTo(200).withFailMessage(resolved.body());
        assertThat(jdbc.queryForObject("SELECT preferred_zone_id FROM d_user WHERE id=?", Long.class, actors.collectorId())).isEqualTo(anotherZone);

        var secondRequest = send("POST", "/api/recolector/solicitudes-zona", collector,
                "{\"zonaId\":" + actors.zoneId() + ",\"motivo\":\"Reconsideración\"}");
        long secondRequestId = numberField(secondRequest.body(), "id");
        var cancelled = send("POST", "/api/recolector/solicitudes-zona/" + secondRequestId + "/cancelar", collector, null);
        assertThat(cancelled.statusCode()).isEqualTo(200).withFailMessage(cancelled.body());
        assertThat(cancelled.body()).contains("CANCELADA");
    }

    private static long numberField(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("JSON field not found: " + field + " in " + json);
        return Long.parseLong(matcher.group(1));
    }
}
