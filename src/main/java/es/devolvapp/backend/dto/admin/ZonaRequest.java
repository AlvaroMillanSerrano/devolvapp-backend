package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
public record ZonaRequest(@NotBlank @Size(max = 80) String nombre,
                          @NotEmpty List<@jakarta.validation.constraints.Pattern(regexp = "\\d{5}") String> codigosPostales) {}
