package es.devolvapp.backend.controller;
import es.devolvapp.backend.dto.common.UsuarioResponse;
import es.devolvapp.backend.dto.perfil.*;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {
    private final PerfilService service;
    @GetMapping
    public UsuarioResponse get(@AuthenticationPrincipal AuthUser user) { return service.get(user.id()); }
    @PutMapping
    public Map<String, Object> update(@AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody ActualizarPerfilRequest request) {
        return service.update(user.id(), request);
    }
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody CambiarPasswordRequest request) {
        service.changePassword(user.id(), request.actual(), request.nueva());
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/password-inicial")
    public UsuarioResponse initialPassword(@AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody PasswordInicialRequest request) {
        return service.createInitialPassword(user.id(), request.nueva());
    }
}
