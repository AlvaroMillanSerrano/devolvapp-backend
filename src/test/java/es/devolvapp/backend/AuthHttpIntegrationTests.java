package es.devolvapp.backend;

import es.devolvapp.backend.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class AuthHttpIntegrationTests extends PostgresIntegrationTest {
    @MockitoBean MailService mailService;

    @Test
    void registersCitizenAndLogsInWithFreshProfile() throws Exception {
        Actors actors = createActors();
        String email = "new-citizen-" + actors.suffix() + "@test.local";
        String registration = """
                {"nombre":"Ana","apellidos":"Test","dni":"12345678Z","telefono":"600123456",
                 "email":"%s","password":"Password-123",
                 "direccion":{"tipoVia":"Calle","nombreVia":"Mayor","numero":"12","codigoPostal":"%s","ciudad":"Málaga"},
                 "aceptaPrivacidad":true}
                """.formatted(email, actors.zip());

        var registered = send("POST", "/api/auth/registro", null, registration);
        assertThat(registered.statusCode()).isEqualTo(201).withFailMessage(registered.body());
        assertThat(registered.body()).contains("ROLE_CIUDADANO", email);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role r JOIN d_user u ON u.id=r.user_id WHERE u.email=? AND r.role='CIUDADANO'",
                Integer.class, email)).isEqualTo(1);

        var login = send("POST", "/api/auth/login", null, "{\"username\":\"%s\",\"password\":\"Password-123\"}".formatted(email));
        assertThat(login.statusCode()).isEqualTo(200).withFailMessage(login.body());
        assertThat(login.body()).contains("accessToken", "refreshToken", "ROLE_CIUDADANO");
        String accessToken = textField(login.body(), "accessToken");
        var profile = send("GET", "/api/auth/me", accessToken, null);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(profile.body()).contains("Ana", "Mayor", actors.zip());
    }

    @Test
    void passwordResetTokenIsSingleUseAndDoesNotRevealUnknownEmails() throws Exception {
        Actors actors = createActors();
        var unknown = send("POST", "/api/auth/password/recuperar", null,
                "{\"email\":\"nobody-%s@test.local\"}".formatted(actors.suffix()));
        assertThat(unknown.statusCode()).isEqualTo(202);

        var requested = send("POST", "/api/auth/password/recuperar", null,
                "{\"email\":\"%s\"}".formatted(actors.citizenEmail()));
        assertThat(requested.statusCode()).isEqualTo(202);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetLink(eq(actors.citizenEmail()), token.capture());

        var valid = send("GET", "/api/auth/password/token/" + token.getValue(), null, null);
        assertThat(valid.statusCode()).isEqualTo(200);
        assertThat(valid.body()).contains("\"valido\":true");
        var reset = send("POST", "/api/auth/password/restablecer", null,
                "{\"token\":\"%s\",\"password\":\"New-Password-456\"}".formatted(token.getValue()));
        assertThat(reset.statusCode()).isEqualTo(204);
        var reused = send("GET", "/api/auth/password/token/" + token.getValue(), null, null);
        assertThat(reused.statusCode()).isEqualTo(410);
        assertThat(reused.body()).contains("TOKEN_EXPIRED");
    }

    @Test
    void registrationRequiresPrivacyConsentAndRejectsDuplicateEmail() throws Exception {
        Actors actors = createActors();
        String email = "duplicate-" + actors.suffix() + "@test.local";
        jdbc.update("UPDATE d_user SET email=? WHERE id=?", email, actors.citizenId());
        String body = """
                {"nombre":"Ana","apellidos":"Test","dni":"12345678Z","telefono":"600123457",
                 "email":"%s","password":"Password-123",
                 "direccion":{"tipoVia":"Calle","nombreVia":"Mayor","numero":"12","codigoPostal":"%s","ciudad":"Málaga"},
                 "aceptaPrivacidad":false}
                """.formatted(email, actors.zip());
        var privacyFailure = send("POST", "/api/auth/registro", null, body);
        assertThat(privacyFailure.statusCode()).isEqualTo(400);
        assertThat(privacyFailure.body()).contains("aceptaPrivacidad");

        body = body.replace("\"aceptaPrivacidad\":false", "\"aceptaPrivacidad\":true");
        var duplicate = send("POST", "/api/auth/registro", null, body);
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(duplicate.body()).contains("DUPLICATE_EMAIL");
    }

    private static String textField(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("JSON field not found: " + field + " in " + json);
        return matcher.group(1);
    }
}
