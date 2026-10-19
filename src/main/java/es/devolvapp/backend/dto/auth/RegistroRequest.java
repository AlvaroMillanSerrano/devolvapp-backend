package es.devolvapp.backend.dto.auth;

import es.devolvapp.backend.dto.common.DireccionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 120) String apellidos,
        @NotBlank @Pattern(regexp = "(?i)(\\d{8}[A-Z]|[XYZ]\\d{7}[A-Z])") String dni,
        @NotBlank @Size(max = 15) String telefono,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull @Valid DireccionDto direccion,
        @AssertTrue(message = "Debe aceptar la política de privacidad") boolean aceptaPrivacidad) {
}
