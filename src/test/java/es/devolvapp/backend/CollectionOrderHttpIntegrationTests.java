package es.devolvapp.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollectionOrderHttpIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    private Long citizenId;
    private Long zoneId;
    private Long timeSlotId;
    private String zipCode;
    private LocalDate collectionDate;

    @BeforeEach
    void createSchedulingAndCitizenData() {
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String zoneName = "Integration-" + uniqueId;
        zipCode = String.format("%05d", 10000 + Math.floorMod(uniqueId.hashCode(), 89999));
        collectionDate = LocalDate.now().plusDays(7);

        zoneId = jdbcTemplate.queryForObject("""
                INSERT INTO zone (name) VALUES (?) RETURNING id
                """, Long.class, zoneName);
        jdbcTemplate.update("""
                INSERT INTO zip_code (code, neighbourhood, lat, lng, zone_id)
                VALUES (?, 'Test neighbourhood', 36.7213, -4.4214, ?)
                """, zipCode, zoneId);

        jdbcTemplate.queryForObject("""
                SELECT setval(
                    pg_get_serial_sequence('d_user', 'id'),
                    COALESCE((SELECT MAX(id) FROM d_user), 1),
                    EXISTS(SELECT 1 FROM d_user)
                )
                """, Long.class);
        citizenId = jdbcTemplate.queryForObject("""
                INSERT INTO d_user (first_name, last_name, email, password_hash)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, "Integration", "Test", uniqueId + "@integration.test", "test-password-hash");
        jdbcTemplate.update("INSERT INTO user_role (user_id, role) VALUES (?, 'CIUDADANO')", citizenId);
        jdbcTemplate.update("""
                INSERT INTO address (user_id, street_type, street_name, street_number, zip_code)
                VALUES (?, 'Calle', 'Test street', '1', ?)
                """, citizenId, zipCode);

        timeSlotId = jdbcTemplate.queryForObject("""
                INSERT INTO time_slot (zone_id, day_of_week, start_time, end_time, capacity)
                VALUES (?, ?, '09:00', '12:00', 10)
                RETURNING id
                """, Long.class, zoneId, collectionDate.getDayOfWeek().getValue());
    }

    @AfterEach
    void removeTestData() {
        if (citizenId != null) {
            jdbcTemplate.update("DELETE FROM collection WHERE citizen_id = ?", citizenId);
            jdbcTemplate.update("DELETE FROM d_user WHERE id = ?", citizenId);
        }
        if (zipCode != null) {
            jdbcTemplate.update("DELETE FROM zip_code WHERE code = ?", zipCode);
        }
        if (zoneId != null) {
            jdbcTemplate.update("DELETE FROM zone WHERE id = ?", zoneId);
        }
    }

    @Test
    void postsCollectionAndPersistsMixedBagsInPostgresql() throws Exception {
        String requestBody = """
                {
                  "citizenId": %d,
                  "franjaId": %d,
                  "fecha": "%s",
                  "bolsas": [
                    {"contenidos": {"PET": 2, "BRIK": 1, "MEZCLADO": 3}},
                    {"contenidos": {"VIDRIO": 4}}
                  ]
                }
                """.formatted(citizenId, timeSlotId, collectionDate);
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ciudadano/pedidos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var idMatcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
                .matcher(response.body());
        assertThat(idMatcher.find()).isTrue();
        long collectionId = Long.parseLong(idMatcher.group(1));
        assertThat(response.body()).containsPattern("\"numBolsas\"\\s*:\\s*2");
        assertThat(response.body()).containsPattern("\"totalEnvases\"\\s*:\\s*10");

        Long savedBags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bag WHERE collection_id = ?", Long.class, collectionId);
        Long savedQuantity = jdbcTemplate.queryForObject(
                "SELECT SUM(quantity) FROM bag WHERE collection_id = ?", Long.class, collectionId);
        Long savedContents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM bag_content bc
                JOIN bag b ON b.id = bc.bag_id
                WHERE b.collection_id = ?
                """, Long.class, collectionId);
        Long savedMixedBag = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM bag b
                WHERE b.collection_id = ?
                  AND b.quantity = 6
                  AND (SELECT COUNT(*) FROM bag_content bc WHERE bc.bag_id = b.id) = 3
                  AND EXISTS (SELECT 1 FROM bag_content bc WHERE bc.bag_id = b.id AND bc.container_type_code = 'PET' AND bc.quantity = 2)
                  AND EXISTS (SELECT 1 FROM bag_content bc WHERE bc.bag_id = b.id AND bc.container_type_code = 'BRIK' AND bc.quantity = 1)
                  AND EXISTS (SELECT 1 FROM bag_content bc WHERE bc.bag_id = b.id AND bc.container_type_code = 'MEZCLADO' AND bc.quantity = 3)
                """, Long.class, collectionId);

        assertThat(savedBags).isEqualTo(2);
        assertThat(savedQuantity).isEqualTo(10);
        assertThat(savedContents).isEqualTo(4);
        assertThat(savedMixedBag).isEqualTo(1);
    }
}
