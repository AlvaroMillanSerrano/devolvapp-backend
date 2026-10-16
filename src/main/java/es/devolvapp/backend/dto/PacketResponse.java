package es.devolvapp.backend.dto;

import es.devolvapp.backend.entity.ContainerType;

import java.util.Map;

public record PacketResponse(Long id, Map<ContainerType, Integer> container, Long estimatedQuantity) {
}
