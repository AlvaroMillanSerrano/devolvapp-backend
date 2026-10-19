package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ResultadoTransferenciaRequest(
        @NotBlank @Pattern(regexp = "COMPLETADA|FALLIDA") String estado,
        @Size(max = 2000) String motivoFallo) {}
