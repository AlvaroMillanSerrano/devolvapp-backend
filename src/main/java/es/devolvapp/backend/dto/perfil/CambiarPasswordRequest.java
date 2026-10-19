package es.devolvapp.backend.dto.perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CambiarPasswordRequest(@NotBlank String actual, @NotBlank @Size(min = 8, max = 100) String nueva) {}
