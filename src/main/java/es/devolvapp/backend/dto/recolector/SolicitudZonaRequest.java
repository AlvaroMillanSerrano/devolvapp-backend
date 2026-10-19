package es.devolvapp.backend.dto.recolector;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record SolicitudZonaRequest(@NotNull Long zonaId, @Size(max = 2000) String motivo) {}
