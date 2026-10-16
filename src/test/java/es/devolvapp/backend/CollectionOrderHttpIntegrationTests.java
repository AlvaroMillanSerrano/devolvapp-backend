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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollectionOrderHttpIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    private Long userId;

    @BeforeEach
    void createTestUser() {
        jdbcTemplate.queryForObject("""
                SELECT setval(
                    pg_get_serial_sequence('d_user', 'id'),
                    COALESCE((SELECT MAX(id) FROM d_user), 1),
                    EXISTS(SELECT 1 FROM d_user)
                )
                """, Long.class);
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        userId = jdbcTemplate.queryForObject("""
                INSERT INTO d_user (u_name, surname, id_card, phone_number, email, password, city, street, zip_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                "Integration", "Test", uniqueId, "600000000", uniqueId + "@integration.test",
                "test-password", "Madrid", "Test street", "28000");
    }

    @AfterEach
    void removeTestUserAndCreatedOrders() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM d_user WHERE id = ?", userId);
        }
    }

    @Test
    void postsOrderAndPersistsItsBagsInPostgresql() throws Exception {
        String requestBody = """
                {
                  "userId": %d,
                  "packets": [
                    {"container": {"PET": 2, "BRIK": 1, "MEZCLADO": 3}},
                    {"container": {"VIDRIO": 4}}
                  ]
                }
                """.formatted(userId);
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/pedidos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var idMatcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
                .matcher(response.body());
        assertThat(idMatcher.find()).isTrue();
        long collectionId = Long.parseLong(idMatcher.group(1));
        assertThat(response.body()).containsPattern("\"totalBags\"\\s*:\\s*2");
        assertThat(response.body()).containsPattern("\"estimatedContainerTotal\"\\s*:\\s*10");

        Long savedBags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM packet WHERE collection_id = ?", Long.class, collectionId);
        Long savedQuantity = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(container_quantity), 0) FROM packet WHERE collection_id = ?",
                Long.class, collectionId);
        Long savedMixedBag = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM packet
                WHERE collection_id = ?
                  AND container = CAST(? AS jsonb)
                """, Long.class, collectionId, "{\"PET\":2,\"BRIK\":1,\"MEZCLADO\":3}");

        assertThat(savedBags).isEqualTo(2);
        assertThat(savedQuantity).isEqualTo(10);
        assertThat(savedMixedBag).isEqualTo(1);
    }
}
