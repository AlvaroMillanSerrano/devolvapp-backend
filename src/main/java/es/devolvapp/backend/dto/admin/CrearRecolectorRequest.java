package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CrearRecolectorRequest(@NotBlank @Size(max=80) String nombre,
                                     @NotBlank @Size(max=120) String apellidos,
                                     @NotBlank @Email @Size(max=160) String email,
                                     @Size(max=15) String telefono,
                                     @Size(max=9) String dni) {}
