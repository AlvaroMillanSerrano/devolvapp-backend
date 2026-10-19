package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.common.PageResponse;
import es.devolvapp.backend.dto.recolector.IncidenciaRequest;
import es.devolvapp.backend.dto.recolector.ResultadoBolsaRequest;
import es.devolvapp.backend.dto.recolector.SolicitudZonaRequest;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.service.ImpactService;
import es.devolvapp.backend.service.RecolectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/recolector")
@RequiredArgsConstructor
public class RecolectorController {
    private final RecolectorService service;
    private final ImpactService impactService;

    @GetMapping("/recogidas")
    public Map<String, Object> assigned(@AuthenticationPrincipal AuthUser user) {
        return service.assigned(user.id());
    }
    @GetMapping("/recogidas/{id}")
    public Map<String, Object> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.detail(user.id(), id);
    }
    @PostMapping("/recogidas/{id}/iniciar")
    public Map<String, Object> start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.start(user.id(), id);
    }
    @PutMapping("/recogidas/{id}/bolsas/{bolsaId}")
    public Map<String, Object> bagResult(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @PathVariable Long bolsaId, @Valid @RequestBody ResultadoBolsaRequest request) {
        return service.recordBagResult(user.id(), id, bolsaId, request);
    }
    @PostMapping("/recogidas/{id}/completar")
    public Map<String, Object> complete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.complete(user.id(), id);
    }
    @PostMapping("/recogidas/{id}/incidencias")
    public ResponseEntity<Map<String, Object>> incident(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @Valid @RequestBody IncidenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reportIncident(user.id(), id, request));
    }
    @GetMapping("/ruta")
    public Map<String, Object> route(@AuthenticationPrincipal AuthUser user, @RequestParam LocalDate fecha,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        return service.route(user.id(), fecha, lat, lng);
    }
    @GetMapping("/historial")
    public PageResponse<Map<String, Object>> history(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(user.id(), page, size);
    }
    @GetMapping("/impacto")
    public Map<String, Object> impact(@AuthenticationPrincipal AuthUser user) {
        return impactService.forCollector(user.id());
    }
    @GetMapping("/preferencias")
    public Map<String, Object> preferences(@AuthenticationPrincipal AuthUser user) {
        return service.preferences(user.id());
    }
    @PutMapping("/preferencias/zona")
    public Map<String, Object> chooseZone(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody es.devolvapp.backend.dto.recolector.ElegirZonaRequest request) {
        return service.choosePreferredZone(user.id(), request.zonaId());
    }
    @PostMapping("/solicitudes-zona")
    public ResponseEntity<Map<String, Object>> requestZone(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody SolicitudZonaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestZoneChange(user.id(), request));
    }
    @PostMapping("/solicitudes-zona/{id}/cancelar")
    public Map<String, Object> cancelZoneRequest(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.cancelZoneRequest(user.id(), id);
    }
}
