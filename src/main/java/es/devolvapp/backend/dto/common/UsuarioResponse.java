package es.devolvapp.backend.dto.common;

import java.util.List;

public record UsuarioResponse(Long id, String nombre, String apellidos, String dni, String telefono,
                              String email, List<String> roles, String estado, boolean debeCambiarPassword,
                              DireccionDto direccion, ZonaRef zonaPreferida) {
    public record ZonaRef(Long id, String nombre) {
    }
}
