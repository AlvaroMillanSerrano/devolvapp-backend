package es.devolvapp.backend;

import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PostgresIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("devolvapp_test")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.mail.host", () -> "");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwtService;
    @Value("${local.server.port}") protected int port;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE withdrawal_history, withdrawal, remittance, incident_history, incident,
                    settlement, bag_content, bag, collection, zone_change_request, zone_day_collector,
                    time_slot, address, refresh_token, password_reset_token, user_role, d_user, zip_code, zone
                RESTART IDENTITY CASCADE
                """);
    }

    protected Actors createActors() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long zoneId = jdbc.queryForObject("INSERT INTO zone(name) VALUES (?) RETURNING id", Long.class, "Zone-" + suffix);
        String zip = String.format("%05d", 20000 + Math.floorMod(suffix.hashCode(), 79999));
        jdbc.update("INSERT INTO zip_code(code,neighbourhood,lat,lng,zone_id) VALUES (?,'Test',36.72,-4.42,?)", zip, zoneId);
        Long citizenId = createUser("citizen-" + suffix + "@test.local", "CIUDADANO", null);
        jdbc.update("""
                INSERT INTO address(user_id,street_type,street_name,street_number,zip_code,city,lat,lng)
                VALUES (?,'Calle','Test', '1',?,'Málaga',36.72,-4.42)
                """, citizenId, zip);
        Long collectorId = createUser("collector-" + suffix + "@test.local", "RECOLECTOR", zoneId);
        Long adminId = createUser("admin-" + suffix + "@test.local", "ADMIN", null);
        jdbc.update("INSERT INTO zone_day_collector(zone_id,day_of_week,collector_id) VALUES (?,?,?)",
                zoneId, LocalDate.now().plusDays(5).getDayOfWeek().getValue(), collectorId);
        return new Actors(citizenId, collectorId, adminId, zoneId, zip, suffix);
    }

    protected Long createUser(String email, String role, Long preferredZone) {
        Long id = jdbc.queryForObject("""
                INSERT INTO d_user(first_name,last_name,email,password_hash,preferred_zone_id)
                VALUES ('Test','User',?,'$2a$10$test-hash',?) RETURNING id
                """, Long.class, email, preferredZone);
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id, role);
        return id;
    }

    protected String token(Long id, String email, Role role) {
        return jwtService.createAccessToken(new AuthUser(id, email, Set.of(role)));
    }

    protected HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected Long createOrder(Actors actors, String citizenToken, int quantity) throws Exception {
        LocalDate date = LocalDate.now().plusDays(5);
        Long slotId = jdbc.queryForObject("""
                INSERT INTO time_slot(zone_id,day_of_week,start_time,end_time,capacity)
                VALUES (?,?,'09:00','12:00',10) RETURNING id
                """, Long.class, actors.zoneId(), date.getDayOfWeek().getValue());
        String json = "{\"franjaId\":%d,\"fecha\":\"%s\",\"bolsas\":[{\"contenidos\":{\"PET\":%d,\"BRIK\":1}}]}"
                .formatted(slotId, date, quantity);
        HttpResponse<String> response = send("POST", "/api/ciudadano/pedidos", citizenToken, json);
        if (response.statusCode() != 201) throw new AssertionError("Could not create collection: " + response.body());
        return jdbc.queryForObject("SELECT id FROM collection WHERE citizen_id=? ORDER BY id DESC LIMIT 1", Long.class,
                actors.citizenId());
    }

    protected record Actors(Long citizenId, Long collectorId, Long adminId, Long zoneId, String zip, String suffix) {
        String citizenEmail() { return "citizen-" + suffix + "@test.local"; }
        String collectorEmail() { return "collector-" + suffix + "@test.local"; }
        String adminEmail() { return "admin-" + suffix + "@test.local"; }
    }
}
