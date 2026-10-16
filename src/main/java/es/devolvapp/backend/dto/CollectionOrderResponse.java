package es.devolvapp.backend.dto;

import java.util.List;

public record CollectionOrderResponse(
        Long id,
        Long userId,
        int totalBags,
        long estimatedContainerTotal,
        List<PacketResponse> packets
) {
}
