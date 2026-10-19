package es.devolvapp.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreateCollectionRequest(
        // Opcionales para permitir pedidos pendientes de programación en el MVP.
        Long franjaId,
        @FutureOrPresent(message = "La fecha de recogida no puede estar en el pasado")
        LocalDate fecha,
        @NotEmpty(message = "La recogida debe incluir al menos una bolsa")
        List<@Valid CreateBagRequest> bolsas
) {
}
