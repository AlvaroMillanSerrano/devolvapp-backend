package es.devolvapp.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateCollectionOrderRequest(
        @NotNull(message = "El usuario es obligatorio")
        Long userId,
        @NotEmpty(message = "El pedido debe incluir al menos una bolsa")
        List<@Valid CreatePacketRequest> packets
) {
}
