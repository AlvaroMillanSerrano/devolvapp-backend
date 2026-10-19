package es.devolvapp.backend.dto.common;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DireccionDto(
        @NotBlank @Size(max = 20) String tipoVia,
        @NotBlank @Size(max = 120) String nombreVia,
        @NotBlank @Size(max = 10) String numero,
        @Size(max = 10) String piso,
        @Size(max = 10) String letra,
        @Pattern(regexp = "\\d{5}|", message = "El código postal debe tener 5 dígitos") String codigoPostal,
        @NotBlank @Size(max = 80) String ciudad,
        Double lat, Double lng) {}
