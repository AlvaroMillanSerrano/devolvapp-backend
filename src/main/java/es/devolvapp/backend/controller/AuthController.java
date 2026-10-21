package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.auth.*;
import es.devolvapp.backend.dto.common.UsuarioResponse;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody RefreshRequest request) {
        authService.logout(user.id(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.currentUser(user.id());
    }
    @PostMapping("/registro")
public ResponseEntity<UsuarioResponse> register(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
    @PostMapping("/password/recuperar")
    public ResponseEntity<Void> recover(@Valid @RequestBody RecuperarRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }
    @GetMapping("/password/token/{token}")
    public java.util.Map<String, Boolean> validateResetToken(@PathVariable String token) {
        return java.util.Map.of("valido", authService.validatePasswordResetToken(token));
    }
    @PostMapping("/password/restablecer")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody RestablecerRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
