package es.devolvapp.backend.dto.recolector;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ResultadoBolsaRequest(@NotNull Resultado resultado, @Min(0) @Max(32767) Integer envasesValidos,
                                    @Size(max = 2000) String nota) {
    public enum Resultado { ACEPTADA, DISCREPANCIA, NO_ENCONTRADA }
}
