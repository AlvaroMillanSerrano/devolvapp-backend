package es.devolvapp.backend.dto;

import es.devolvapp.backend.entity.ContainerType;

import java.util.Map;

public record BagResponse(
        Long id,
        String codigo,
        Map<ContainerType, Integer> contenidos,
        short cantidad,
        short capacidadMaxima
) {
}
