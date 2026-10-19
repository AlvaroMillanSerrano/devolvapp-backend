package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.auth.LoginResponse;
import es.devolvapp.backend.dto.auth.RegistroRequest;
import es.devolvapp.backend.dto.common.DireccionDto;
import es.devolvapp.backend.dto.common.UsuarioResponse;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.exception.ApiException;
import es.devolvapp.backend.repository.UserRepository;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final MailService mailService;

    @Value("$" + "{app.jwt.refresh-ttl-days:30}")
    private long refreshTtlDays;

    @Transactional
    public LoginResponse login(String username, String password) {
        loginAttempts.check(username);
        User user = users.findByEmailIgnoreCaseOrIdCardIgnoreCase(username, username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            loginAttempts.failed(username);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                    "El usuario o la contraseña no son correctos");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "La cuenta está suspendida");
        }
        loginAttempts.succeeded(username);
        return tokensFor(user, issueRefreshToken(user.getId()));
    }

    @Transactional
    public UsuarioResponse register(RegistroRequest request) {
        if (users.existsByEmail(request.email())) {
            throw ApiException.fieldError("DUPLICATE_EMAIL", "El email ya está registrado",
                    "email", "Este email ya está registrado");
        }
        if (users.existsByIdCard(request.dni())) {
            throw ApiException.fieldError("DUPLICATE_DNI", "El DNI/NIE ya está registrado",
                    "dni", "Este DNI/NIE ya está registrado");
        }
        if (users.existsByPhoneNumber(request.telefono())) {
            throw ApiException.fieldError("DUPLICATE_TELEFONO", "El teléfono ya está registrado",
                    "telefono", "Este teléfono ya está registrado");
        }
        DireccionDto address = request.direccion();
        String postalCode = address.codigoPostal();
        if (postalCode != null && postalCode.isBlank()) postalCode = null;
        if (postalCode != null) {
            Integer postalExists = jdbc.queryForObject("SELECT COUNT(*) FROM zip_code WHERE code = ?",
                    Integer.class, postalCode);
            if (postalExists == null || postalExists == 0) {
                throw ApiException.fieldError("VALIDATION_ERROR", "El código postal no está cubierto",
                        "direccion.codigoPostal", "El código postal no está disponible");
            }
        }
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO d_user (first_name, last_name, email, id_card, phone_number, password_hash)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, request.nombre(), request.apellidos(), request.email(),
                    request.dni().toUpperCase(), request.telefono(), passwordEncoder.encode(request.password()));
            jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'CIUDADANO')", id);
            jdbc.update("""
                    INSERT INTO address (user_id, street_type, street_name, street_number, floor, letter,
                                         zip_code, city, lat, lng)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                            (SELECT lat FROM zip_code WHERE code = ?),
                            (SELECT lng FROM zip_code WHERE code = ?))
                    """, id, address.tipoVia(), address.nombreVia(), address.numero(), address.piso(),
                    address.letra(), postalCode, address.ciudad(), postalCode, postalCode);
            return userResponse(id);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("DUPLICATE_EMAIL", "Ya existe una cuenta con esos datos");
        }
    }

    @Transactional
    public void logout(Long userId, String refreshToken) {
        jdbc.update("""
                UPDATE refresh_token SET revoked_at = now()
                WHERE user_id = ? AND token_hash = ? AND revoked_at IS NULL
                """, userId, hash(refreshToken));
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        List<Long> ids = jdbc.query("""
                SELECT user_id FROM refresh_token
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now() FOR UPDATE
                """, (rs, row) -> rs.getLong("user_id"), hash(refreshToken));
        if (ids.isEmpty()) {
            throw ApiException.fieldError("UNAUTHORIZED", "El refresh token no es válido",
                    "refreshToken", "Inicia sesión de nuevo");
        }
        Long id = ids.getFirst();
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "La cuenta está suspendida");
        }
        jdbc.update("UPDATE refresh_token SET revoked_at = now() WHERE token_hash = ?", hash(refreshToken));
        return tokensFor(user, issueRefreshToken(id));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(email).ifPresent(user -> {
            String token = randomToken();
            jdbc.update("INSERT INTO password_reset_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                    user.getId(), hash(token), OffsetDateTime.now().plusMinutes(30));
            mailService.sendPasswordResetLink(email, token);
        });
    }

    public boolean validatePasswordResetToken(String token) {
        List<Boolean> rows = jdbc.query("""
                SELECT used_at IS NULL AND expires_at > now() AS valid
                FROM password_reset_token WHERE token_hash = ?
                """, (rs, row) -> rs.getBoolean("valid"), hash(token));
        if (rows.isEmpty()) throw ApiException.notFound("El token no existe");
        if (!rows.getFirst()) throw new ApiException(HttpStatus.GONE, "TOKEN_EXPIRED",
                "El enlace ha caducado o ya se ha utilizado");
        return true;
    }

    @Transactional
    public void resetPassword(String token, String password) {
        List<Long> ids = jdbc.query("""
                SELECT user_id FROM password_reset_token
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > now() FOR UPDATE
                """, (rs, row) -> rs.getLong("user_id"), hash(token));
        if (ids.isEmpty()) {
            validatePasswordResetToken(token);
            throw new ApiException(HttpStatus.GONE, "TOKEN_EXPIRED", "El enlace ha caducado o ya se ha utilizado");
        }
        Long id = ids.getFirst();
        jdbc.update("UPDATE d_user SET password_hash = ?, must_change_password = false, updated_at = now() WHERE id = ?",
                passwordEncoder.encode(password), id);
        jdbc.update("UPDATE password_reset_token SET used_at = now() WHERE token_hash = ?", hash(token));
        jdbc.update("UPDATE refresh_token SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL", id);
    }

    public UsuarioResponse currentUser(Long id) {
        return userResponse(id);
    }

    private LoginResponse tokensFor(User user, String refreshToken) {
        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), Set.copyOf(user.getRoles()));
        return new LoginResponse(jwtService.createAccessToken(principal), refreshToken, "Bearer",
                jwtService.getAccessTtlSeconds(), userResponse(user.getId()));
    }

    private UsuarioResponse userResponse(Long id) {
        List<UsuarioResponse> rows = jdbc.query("""
                SELECT u.id, u.first_name, u.last_name, u.id_card, u.phone_number, u.email, u.status,
                       u.must_change_password, u.preferred_zone_id,
                       a.street_type, a.street_name, a.street_number, a.floor, a.letter,
                       RTRIM(a.zip_code) AS zip_code, a.city, a.lat, a.lng, z.name AS preferred_zone_name
                FROM d_user u LEFT JOIN address a ON a.user_id = u.id
                LEFT JOIN zone z ON z.id = u.preferred_zone_id WHERE u.id = ?
                """, (rs, row) -> {
            var latValue = rs.getBigDecimal("lat");
            var lngValue = rs.getBigDecimal("lng");
            DireccionDto address = rs.getString("street_name") == null ? null : new DireccionDto(
                    rs.getString("street_type"), rs.getString("street_name"), rs.getString("street_number"),
                    rs.getString("floor"), rs.getString("letter"), rs.getString("zip_code"), rs.getString("city"),
                    latValue == null ? null : latValue.doubleValue(), lngValue == null ? null : lngValue.doubleValue());
            Object zoneId = rs.getObject("preferred_zone_id");
            UsuarioResponse.ZonaRef zone = zoneId == null ? null : new UsuarioResponse.ZonaRef(
                    ((Number) zoneId).longValue(), rs.getString("preferred_zone_name"));
            List<String> roles = jdbc.query("SELECT role FROM user_role WHERE user_id = ?",
                    (roleRs, index) -> "ROLE_" + roleRs.getString("role"), id);
            return new UsuarioResponse(rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"),
                    rs.getString("id_card"), rs.getString("phone_number"), rs.getString("email"), roles,
                    rs.getString("status"), rs.getBoolean("must_change_password"), address, zone);
        }, id);
        if (rows.isEmpty()) throw ApiException.notFound("Usuario no encontrado");
        return rows.getFirst();
    }

    private String issueRefreshToken(Long userId) {
        String raw = randomToken();
        jdbc.update("INSERT INTO refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, hash(raw), OffsetDateTime.now().plusDays(refreshTtlDays));
        return raw;
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }
}
