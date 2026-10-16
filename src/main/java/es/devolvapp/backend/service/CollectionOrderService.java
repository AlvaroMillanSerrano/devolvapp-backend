package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.CollectionOrderResponse;
import es.devolvapp.backend.dto.CreateCollectionOrderRequest;
import es.devolvapp.backend.dto.PacketResponse;
import es.devolvapp.backend.entity.CollectionOrder;
import es.devolvapp.backend.entity.Packet;
import es.devolvapp.backend.repository.CollectionOrderRepository;
import es.devolvapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CollectionOrderService {

    private final CollectionOrderRepository collectionOrderRepository;
    private final UserRepository userRepository;

    @Transactional
    public CollectionOrderResponse create(CreateCollectionOrderRequest request) {
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        var collection = new CollectionOrder();
        collection.setUser(user);
        collection.setCollectionStartDate(LocalDateTime.now());
        collection.setState("pendiente");

        request.packets().forEach(packetRequest -> {
            var packet = new Packet();
            packet.setContainer(packetRequest.container());
            long estimatedQuantity = packetRequest.container().values().stream()
                    .mapToLong(Integer::longValue)
                    .sum();
            packet.setContainerQuantity(estimatedQuantity);
            collection.addPacket(packet);
        });

        var saved = collectionOrderRepository.save(collection);
        var packets = saved.getPackets().stream()
                .map(packet -> new PacketResponse(
                        packet.getId(), packet.getContainer(), packet.getContainerQuantity()))
                .toList();
        long estimatedTotal = packets.stream()
                .mapToLong(PacketResponse::estimatedQuantity)
                .sum();

        return new CollectionOrderResponse(
                saved.getId(),
                user.getId(),
                packets.size(),
                estimatedTotal,
                packets
        );
    }

}
