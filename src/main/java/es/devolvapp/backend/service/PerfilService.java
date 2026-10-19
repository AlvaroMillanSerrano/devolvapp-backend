package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.common.DireccionDto;
import es.devolvapp.backend.dto.common.UsuarioResponse;
import es.devolvapp.backend.dto.perfil.ActualizarPerfilRequest;
import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.exception.ApiException;
import es.devolvapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerfilService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UsuarioResponse get(Long userId) {
        return authService.currentUser(userId);
    }

    @Transactional
    public Map<String, Object> update(Long userId, ActualizarPerfilRequest request) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (users.findByEmailIgnoreCaseOrIdCardIgnoreCase(request.email(), request.email())
                .filter(other -> !other.getId().equals(userId)).isPresent()) {
            throw ApiException.fieldError("DUPLICATE_EMAIL", "El email ya está registrado",
                    "email", "Este email ya está registrado");
        }
        Integer phoneCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM d_user WHERE phone_number = ? AND id <> ?", Integer.class,
                request.telefono(), userId);
        if (phoneCount != null && phoneCount > 0) {
            throw ApiException.fieldError("DUPLICATE_TELEFONO", "El teléfono ya está registrado",
                    "telefono", "Este teléfono ya está registrado");
        }
        String warning = null;
        jdbc.update("UPDATE d_user SET first_name=?, last_name=?, email=?, phone_number=?, updated_at=now() WHERE id=?",
                request.nombre(), request.apellidos(), request.email(), request.telefono(), userId);
        if (request.direccion() != null && user.getRoles().contains(Role.CIUDADANO)) {
            DireccionDto address = request.direccion();
            String previousZip = jdbc.query("SELECT RTRIM(zip_code) FROM address WHERE user_id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, userId);
            var zip = jdbc.query("""
                    SELECT zone_id, lat, lng FROM zip_code WHERE code = ?
                    """, (rs, row) -> new Object[]{rs.getObject("zone_id"), rs.getBigDecimal("lat"), rs.getBigDecimal("lng")},
                    address.codigoPostal());
            if (zip.isEmpty()) throw ApiException.fieldError("VALIDATION_ERROR", "Código postal desconocido",
                    "direccion.codigoPostal", "El código postal no está disponible");
            Object[] target = zip.getFirst();
            Object oldZone = previousZip == null ? null : jdbc.query(
                    "SELECT zone_id FROM zip_code WHERE code = ?", rs -> rs.next() ? rs.getObject(1) : null,
                    previousZip);
            Object newZone = target[0];
            jdbc.update("""
                    UPDATE address SET street_type=?, street_name=?, street_number=?, floor=?, letter=?,
                                       zip_code=?, city=?, lat=?, lng=? WHERE user_id=?
                    """, address.tipoVia(), address.nombreVia(), address.numero(), address.piso(), address.letra(),
                    address.codigoPostal(), address.ciudad(),
                    target[1] == null ? null : ((java.math.BigDecimal) target[1]).doubleValue(),
                    target[2] == null ? null : ((java.math.BigDecimal) target[2]).doubleValue(), userId);
            if (oldZone == null || newZone == null || !oldZone.equals(newZone))
                warning = newZone == null ? "El nuevo código postal está fuera de una zona con servicio"
                        : "El cambio de zona afecta a la cobertura del servicio";
        }
        UsuarioResponse response = authService.currentUser(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usuario", response);
        result.put("avisoZona", warning);
        return result;
    }

    @Transactional
    public void changePassword(Long userId, String current, String next) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (!passwordEncoder.matches(current, user.getPassword())) {
            throw ApiException.fieldError("VALIDATION_ERROR", "La contraseña actual no es correcta",
                    "actual", "La contraseña actual no es correcta");
        }
        jdbc.update("UPDATE d_user SET password_hash=?, must_change_password=false, updated_at=now() WHERE id=?",
                passwordEncoder.encode(next), userId);
    }

    @Transactional
    public UsuarioResponse createInitialPassword(Long userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        if (!user.isMustChangePassword())
            throw ApiException.conflict("NO_PERMITIDO", "La cuenta no requiere crear una contraseña inicial");
        jdbc.update("UPDATE d_user SET password_hash=?, must_change_password=false, updated_at=now() WHERE id=?",
                passwordEncoder.encode(password), userId);
        return authService.currentUser(userId);
    }
}
