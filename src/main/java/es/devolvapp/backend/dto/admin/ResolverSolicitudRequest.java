package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ResolverSolicitudRequest(boolean aprobar, boolean moverDias, @Size(max = 2000) String respuesta) {}
