package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ActualizarIncidenciaRequest(
        @Pattern(regexp = "ABIERTA|EN_REVISION|RESUELTA") String estado,
        @Size(max = 4000) String resolucion) {}
