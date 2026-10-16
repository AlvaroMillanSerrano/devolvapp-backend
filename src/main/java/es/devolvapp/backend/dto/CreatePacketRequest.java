package es.devolvapp.backend.dto;

import es.devolvapp.backend.entity.ContainerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record CreatePacketRequest(
        @NotEmpty(message = "La bolsa debe incluir al menos un tipo de envase")
        Map<@NotNull ContainerType, @NotNull @Positive(message = "La cantidad estimada por tipo debe ser mayor que cero") Integer> container
) {
}
