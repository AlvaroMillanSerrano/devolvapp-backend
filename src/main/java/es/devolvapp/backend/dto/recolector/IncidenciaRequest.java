package es.devolvapp.backend.dto.recolector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record IncidenciaRequest(@NotBlank @Size(max = 30) String tipo,
                                @NotBlank @Size(max = 4000) String descripcion) {}
