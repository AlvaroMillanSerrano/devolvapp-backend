package es.devolvapp.backend.dto.perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PasswordInicialRequest(@NotBlank @Size(min = 8, max = 100) String nueva) {}
