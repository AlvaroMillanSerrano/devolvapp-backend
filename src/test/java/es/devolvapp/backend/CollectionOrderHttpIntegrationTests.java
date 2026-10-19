package es.devolvapp.backend;

import es.devolvapp.backend.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionOrderHttpIntegrationTests extends PostgresIntegrationTest {
    private static final String IBAN = "ES9121000418450200051332";

    @Test
    void createsMixedBagOrderWithoutZoneCollectorOrSchedule() throws Exception {
        Actors actors = createActors();
        String citizen = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        jdbc.update("UPDATE zip_code SET zone_id=NULL WHERE code=?", actors.zip());

        HttpResponse<String> response = send("POST", "/api/ciudadano/pedidos", citizen,
                "{\"bolsas\":[{\"contenidos\":{\"PET\":2,\"BRIK\":1}},"
                        + "{\"contenidos\":{\"VIDRIO\":3}}]}");

        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.body()).contains("\"numBolsas\":2", "\"totalEnvases\":6",
                "\"PET\":2", "\"BRIK\":1", "\"VIDRIO\":3");
        Long collectionId = jdbc.queryForObject("SELECT id FROM collection WHERE citizen_id=?", Long.class, actors.citizenId());
        var saved = jdbc.queryForMap("""
                SELECT zone_id,collector_id,time_slot_id,collection_date,start_time,end_time
                FROM collection WHERE id=?
                """, collectionId);
        assertThat(saved.get("zone_id")).isNull();
        assertThat(saved.get("collector_id")).isNull();
        assertThat(saved.get("time_slot_id")).isNull();
        assertThat(saved.get("collection_date")).isNull();
        assertThat(saved.get("start_time")).isNull();
        assertThat(saved.get("end_time")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bag WHERE collection_id=?", Integer.class, collectionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM bag WHERE collection_id=?", Integer.class, collectionId)).isEqualTo(6);
    }

    @Test
    void persistsMixedContentsAndReturnsBagAndContainerTotals() throws Exception {
        Actors actors = createActors();
        String citizenToken = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        LocalDate date = LocalDate.now().plusDays(5);
        Long slotId = jdbc.queryForObject("""
                INSERT INTO time_slot(zone_id,day_of_week,start_time,end_time,capacity)
                VALUES (?,?,'09:00','12:00',10) RETURNING id
                """, Long.class, actors.zoneId(), date.getDayOfWeek().getValue());

        String json = ("{\"franjaId\":%d,\"fecha\":\"%s\",\"bolsas\":["
                + "{\"contenidos\":{\"PET\":2,\"BRIK\":1,\"MEZCLADO\":3}},"
                + "{\"contenidos\":{\"VIDRIO\":4}}]}").formatted(slotId, date);
        HttpResponse<String> response = send("POST", "/api/ciudadano/pedidos", citizenToken, json);

        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.body()).contains("\"numBolsas\":2", "\"totalEnvases\":10");
        Long collectionId = jdbc.queryForObject("SELECT id FROM collection WHERE citizen_id=?", Long.class, actors.citizenId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bag WHERE collection_id=?", Integer.class, collectionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM bag WHERE collection_id=?", Integer.class, collectionId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bag_content bc JOIN bag b ON b.id=bc.bag_id
                WHERE b.collection_id=? AND b.code IS NOT NULL
                """, Integer.class, collectionId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bag b WHERE b.collection_id=? AND b.quantity=6
                  AND (SELECT COUNT(*) FROM bag_content bc WHERE bc.bag_id=b.id)=3
                  AND EXISTS(SELECT 1 FROM bag_content bc WHERE bc.bag_id=b.id AND bc.container_type_code='PET' AND bc.quantity=2)
                  AND EXISTS(SELECT 1 FROM bag_content bc WHERE bc.bag_id=b.id AND bc.container_type_code='BRIK' AND bc.quantity=1)
                  AND EXISTS(SELECT 1 FROM bag_content bc WHERE bc.bag_id=b.id AND bc.container_type_code='MEZCLADO' AND bc.quantity=3)
                """, Integer.class, collectionId)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidBagQuantitiesAndEnforcesRoleAccess() throws Exception {
        Actors actors = createActors();
        String citizen = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        String collector = token(actors.collectorId(), actors.collectorEmail(), Role.RECOLECTOR);
        LocalDate date = LocalDate.now().plusDays(5);
        Long slotId = jdbc.queryForObject("""
                INSERT INTO time_slot(zone_id,day_of_week,start_time,end_time,capacity)
                VALUES (?,?,'09:00','12:00',10) RETURNING id
                """, Long.class, actors.zoneId(), date.getDayOfWeek().getValue());
        String zeroBag = "{\"franjaId\":%d,\"fecha\":\"%s\",\"bolsas\":[{\"contenidos\":{\"PET\":0}}]}"
                .formatted(slotId, date);

        HttpResponse<String> noAuth = send("POST", "/api/ciudadano/pedidos", null, zeroBag);
        HttpResponse<String> invalid = send("POST", "/api/ciudadano/pedidos", citizen, zeroBag);
        HttpResponse<String> wrongRole = send("GET", "/api/ciudadano/monedero", collector, null);

        assertThat(noAuth.statusCode()).isEqualTo(401);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("VALIDATION_ERROR", "bolsas[0].contenidos[PET]");
        assertThat(wrongRole.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM collection", Integer.class)).isZero();
    }

    @Test
    void runsCollectionIncidentSettlementWalletAndWithdrawalFlow() throws Exception {
        Actors actors = createActors();
        String citizen = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        String collector = token(actors.collectorId(), actors.collectorEmail(), Role.RECOLECTOR);
        String admin = token(actors.adminId(), actors.adminEmail(), Role.ADMIN);
        Long collectionId = createOrder(actors, citizen, 5);
        Long bagId = jdbc.queryForObject("SELECT id FROM bag WHERE collection_id=?", Long.class, collectionId);

        assertThat(send("POST", "/api/recolector/recogidas/" + collectionId + "/iniciar", collector, "{}").statusCode()).isEqualTo(200);
        HttpResponse<String> verification = send("PUT", "/api/recolector/recogidas/" + collectionId + "/bolsas/" + bagId,
                collector, "{\"resultado\":\"DISCREPANCIA\",\"envasesValidos\":4,\"nota\":\"Envase dañado\"}");
        assertThat(verification.statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/recolector/recogidas/" + collectionId + "/completar", collector, "{}").statusCode()).isEqualTo(200);

        var settlement = jdbc.queryForMap("SELECT valid_containers,total_amount,citizen_share,company_share FROM settlement WHERE collection_id=?", collectionId);
        assertThat(settlement.get("valid_containers")).isEqualTo(4);
        assertThat(settlement.get("total_amount").toString()).isEqualTo("0.40");
        assertThat(settlement.get("citizen_share").toString()).isEqualTo("0.16");
        assertThat(settlement.get("company_share").toString()).isEqualTo("0.24");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE collection_id=? AND status='ABIERTA'", Integer.class, collectionId)).isEqualTo(1);

        HttpResponse<String> wallet = send("GET", "/api/ciudadano/monedero", citizen, null);
        assertThat(wallet.statusCode()).isEqualTo(200);
        assertThat(wallet.body()).contains("0.16");
        HttpResponse<String> withdrawal = send("POST", "/api/ciudadano/monedero/retiradas", citizen,
                "{\"importe\":0.10,\"iban\":\"%s\"}".formatted(IBAN));
        assertThat(withdrawal.statusCode()).isEqualTo(201).withFailMessage(withdrawal.body());
        Long withdrawalId = numberField(withdrawal.body(), "id");
        HttpResponse<String> failPayment = send("POST", "/api/admin/transferencias/" + withdrawalId + "/resultado", admin,
                "{\"estado\":\"FALLIDA\",\"motivoFallo\":\"Rechazada por banco\"}");
        assertThat(failPayment.statusCode()).isEqualTo(200).withFailMessage(failPayment.body());
        assertThat(send("GET", "/api/ciudadano/monedero", citizen, null).body()).contains("0.16");

        HttpResponse<String> pendingWithdrawal = send("POST", "/api/ciudadano/monedero/retiradas", citizen,
                "{\"importe\":0.06,\"iban\":\"%s\"}".formatted(IBAN));
        assertThat(pendingWithdrawal.statusCode()).isEqualTo(201).withFailMessage(pendingWithdrawal.body());
        Long pendingId = numberField(pendingWithdrawal.body(), "id");
        HttpResponse<String> batch = send("POST", "/api/admin/transferencias/pagar-lote", admin,
                "{\"ids\":[%d]}".formatted(pendingId));
        assertThat(batch.statusCode()).isEqualTo(200).withFailMessage(batch.body());
        assertThat(batch.body()).contains("\"procesadas\":1", "REM-");
        assertThat(jdbc.queryForObject("SELECT status FROM withdrawal WHERE id=?", String.class, pendingId)).isEqualTo("COMPLETADA");
        HttpResponse<String> duplicateBatch = send("POST", "/api/admin/transferencias/pagar-lote", admin,
                "{\"ids\":[%d]}".formatted(pendingId));
        assertThat(duplicateBatch.statusCode()).isEqualTo(409);
        assertThat(duplicateBatch.body()).contains("YA_PROCESADA");

        HttpResponse<String> incidence = send("GET", "/api/admin/incidencias?estado=ABIERTA", admin, null);
        assertThat(incidence.statusCode()).isEqualTo(200);
        Long incidentId = numberField(incidence.body(), "id");
        HttpResponse<String> resolved = send("PATCH", "/api/admin/incidencias/" + incidentId, admin,
                "{\"estado\":\"RESUELTA\",\"resolucion\":\"Incidencia revisada\"}");
        assertThat(resolved.statusCode()).isEqualTo(200).withFailMessage(resolved.body());
        assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?", String.class, incidentId)).isEqualTo("RESUELTA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_history WHERE incident_id=?", Integer.class, incidentId)).isEqualTo(2);
    }

    @Test
    void preventsCompletingCollectionUntilEveryBagIsVerified() throws Exception {
        Actors actors = createActors();
        String citizen = token(actors.citizenId(), actors.citizenEmail(), Role.CIUDADANO);
        String collector = token(actors.collectorId(), actors.collectorEmail(), Role.RECOLECTOR);
        Long collectionId = createOrder(actors, citizen, 2);
        assertThat(send("POST", "/api/recolector/recogidas/" + collectionId + "/iniciar", collector, "{}").statusCode()).isEqualTo(200);

        HttpResponse<String> complete = send("POST", "/api/recolector/recogidas/" + collectionId + "/completar", collector, "{}");

        assertThat(complete.statusCode()).isEqualTo(409);
        assertThat(complete.body()).contains("BOLSAS_SIN_VERIFICAR");
        assertThat(jdbc.queryForObject("SELECT status FROM collection WHERE id=?", String.class, collectionId)).isEqualTo("EN_CAMINO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement WHERE collection_id=?", Integer.class, collectionId)).isZero();
    }

    private static Long numberField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("JSON field not found: " + field + " in " + json);
        return Long.parseLong(matcher.group(1));
    }
}
