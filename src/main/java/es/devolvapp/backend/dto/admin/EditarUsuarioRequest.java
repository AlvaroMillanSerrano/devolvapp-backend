package es.devolvapp.backend.dto.admin;
import es.devolvapp.backend.dto.common.DireccionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record EditarUsuarioRequest(@NotBlank @Size(max=80) String nombre,
                                   @NotBlank @Size(max=120) String apellidos,
                                   @NotBlank @Email @Size(max=160) String email,
                                   @Size(max=15) String telefono,
                                   @Valid DireccionDto direccion) {}
