package es.devolvapp.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateCollectionRequest(
        @NotNull(message = "La franja de recogida es obligatoria")
        Long franjaId,
        @NotNull(message = "La fecha de recogida es obligatoria")
        @FutureOrPresent(message = "La fecha de recogida no puede estar en el pasado")
        LocalDate fecha,
        @NotEmpty(message = "La recogida debe incluir al menos una bolsa")
        List<@Valid CreateBagRequest> bolsas
) {
}
