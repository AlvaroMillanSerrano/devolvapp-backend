package es.devolvapp.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record CollectionOrderResponse(
        Long id,
        String codigo,
        Long ciudadanoId,
        Long zonaId,
        OffsetDateTime creadoEn,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String estado,
        int numBolsas,
        long totalEnvases,
        List<BagResponse> bolsas
) {
}
