package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.ciudadano.RetiradaRequest;
import es.devolvapp.backend.dto.common.PageResponse;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.service.CiudadanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ciudadano")
@RequiredArgsConstructor
public class CiudadanoController {
    private final CiudadanoService service;

    @GetMapping("/pedidos")
    public PageResponse<Map<String, Object>> orders(@AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listOrders(user.id(), estado, page, size);
    }

    @GetMapping("/pedidos/{id}")
    public Map<String, Object> order(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.getOrder(user.id(), id);
    }

    @GetMapping("/disponibilidad")
    public Map<String, Object> availability(@AuthenticationPrincipal AuthUser user) {
        return service.availability(user.id());
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public Map<String, Object> cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.cancelOrder(user.id(), id);
    }

    @GetMapping("/impacto")
    public Map<String, Object> impact(@AuthenticationPrincipal AuthUser user) {
        return service.impact(user.id());
    }

    @GetMapping("/monedero")
    public Map<String, Object> wallet(@AuthenticationPrincipal AuthUser user) {
        return service.wallet(user.id());
    }

    @GetMapping("/monedero/movimientos")
    public PageResponse<Map<String, Object>> movements(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.movements(user.id(), page, size);
    }

    @PostMapping("/monedero/retiradas")
    public ResponseEntity<Map<String, Object>> withdraw(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody RetiradaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestWithdrawal(user.id(), request));
    }
}
