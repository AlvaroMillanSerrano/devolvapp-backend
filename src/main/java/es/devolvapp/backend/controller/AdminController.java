package es.devolvapp.backend.controller;

import es.devolvapp.backend.dto.admin.*;
import es.devolvapp.backend.security.AuthUser;
import es.devolvapp.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService service;
    @GetMapping("/pendientes") public Object pendientes(){return service.dashboard();}
    @GetMapping("/indicadores") public Object indicadores(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta){return service.indicators(desde,hasta);}
    @GetMapping("/incidencias") public Object incidencias(@RequestParam(required=false) String tipo,@RequestParam(required=false) String estado,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.incidents(tipo,estado,page,size);}
    @GetMapping("/incidencias/{id}") public Object incidencia(@PathVariable Long id){return service.incident(id);}
    @PatchMapping("/incidencias/{id}") public Object actualizarIncidencia(@PathVariable Long id,@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ActualizarIncidenciaRequest request){return service.updateIncident(id,user.id(),request);}
    @GetMapping("/recogidas") public Object recogidas(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required=false) String estado,@RequestParam(required=false) Long zonaId,@RequestParam(required=false) String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.collections(fecha,estado,zonaId,q,page,size);}
    @GetMapping("/recogidas/{id}") public Object recogida(@PathVariable Long id){return service.adminCollectionDetail(id);}
    @PutMapping("/recogidas/{id}/recolector") public Object reasignar(@PathVariable Long id,@Valid @RequestBody ReasignarRecolectorRequest request){return service.assignCollector(id,request.recolectorId());}
    @PostMapping("/recogidas/{id}/cancelar") public Object cancelar(@PathVariable Long id,@AuthenticationPrincipal AuthUser user){return service.cancelCollection(id,user.id());}
    @GetMapping("/transferencias") public Object transferencias(@RequestParam(required=false) String estado,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size){return service.withdrawals(estado,page,size);}
    @GetMapping("/transferencias/{id}") public Object transferencia(@PathVariable Long id){return service.withdrawal(id);}
    @PostMapping("/transferencias/{id}/resultado") public Object resultado(@PathVariable Long id,@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ResultadoTransferenciaRequest request){return service.setWithdrawalResult(id,user.id(),request);}
    @PostMapping("/transferencias/pagar-lote") public Object pagarLote(@AuthenticationPrincipal AuthUser user,@Valid @RequestBody PagarLoteRequest request){return service.payBatch(user.id(),request.ids());}
    @GetMapping("/codigos-postales") public Object codigosPostales(){return service.postalCodes(true);}
    @GetMapping("/zonas") public Object zonas(){return service.zones();}
    @GetMapping("/recolectores") public Object recolectores(){return service.collectors();}
    @PostMapping("/zonas") @ResponseStatus(HttpStatus.CREATED) public Object crearZona(@Valid @RequestBody ZonaRequest request){return service.createZone(request);}
    @PutMapping("/zonas/{id}") public Object editarZona(@PathVariable Long id,@Valid @RequestBody ZonaRequest request){return service.updateZone(id,request);}
    @DeleteMapping("/zonas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void borrarZona(@PathVariable Long id){service.deleteZone(id);}
    @PutMapping("/zonas/{id}/dias/{dia}/recolector") public Object asignarDia(@PathVariable Long id,@PathVariable int dia,@RequestBody AsignarDiaRequest request){return service.assignDay(id,dia,request.recolectorId());}
    @GetMapping("/solicitudes-zona") public Object solicitudes(@RequestParam(required=false) String estado){return service.zoneRequests(estado);}
    @PostMapping("/solicitudes-zona/{id}/resolver") public Object resolver(@PathVariable Long id,@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ResolverSolicitudRequest request){return service.resolveZoneRequest(id,user.id(),request);}
    @PostMapping("/zonas/{id}/franjas") @ResponseStatus(HttpStatus.CREATED) public Object crearFranja(@PathVariable Long id,@Valid @RequestBody FranjaRequest request){return service.createSlot(id,request);}
    @PutMapping("/franjas/{id}") public Object editarFranja(@PathVariable Long id,@Valid @RequestBody FranjaRequest request){return service.updateSlot(id,request);}
    @DeleteMapping("/franjas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void borrarFranja(@PathVariable Long id){service.deleteSlot(id);}
    @GetMapping("/usuarios") public Object usuarios(@RequestParam(required=false) String q,@RequestParam(required=false) String rol,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.users(q,rol,page,size);}
    @GetMapping("/usuarios/{id}") public Object usuario(@PathVariable Long id){return service.user(id);}
    @PutMapping("/usuarios/{id}") public Object editarUsuario(@PathVariable Long id,@Valid @RequestBody EditarUsuarioRequest request){return service.editUser(id,request);}
    @PutMapping("/usuarios/{id}/roles") public Object actualizarRoles(@PathVariable Long id,@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ActualizarRolesRequest request){return service.updateUserRoles(id,user.id(),request);}
    @PostMapping("/usuarios/{id}/suspender") public Object suspender(@PathVariable Long id){return service.setUserStatus(id,false);}
    @PostMapping("/usuarios/{id}/reactivar") public Object reactivar(@PathVariable Long id){return service.setUserStatus(id,true);}
    @PostMapping("/recolectores") @ResponseStatus(HttpStatus.CREATED) public Object crearRecolector(@Valid @RequestBody CrearRecolectorRequest request){return service.createCollector(request);}
    @PostMapping("/usuarios/{id}/password-provisional") public Object passwordProvisional(@PathVariable Long id){return service.temporaryPassword(id);}
}
