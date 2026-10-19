package es.devolvapp.backend.dto.ciudadano;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
public record RetiradaRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal importe,
                              @NotBlank @Size(max = 34) String iban) {}
