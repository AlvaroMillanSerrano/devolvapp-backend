package es.devolvapp.backend;

import es.devolvapp.backend.dto.CreateCollectionOrderRequest;
import es.devolvapp.backend.dto.CreatePacketRequest;
import es.devolvapp.backend.entity.CollectionOrder;
import es.devolvapp.backend.entity.ContainerType;
import es.devolvapp.backend.entity.Packet;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.repository.CollectionOrderRepository;
import es.devolvapp.backend.repository.UserRepository;
import es.devolvapp.backend.service.CollectionOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CollectionOrderServiceTests {

    @Test
    void savesSeveralBagsForTheSameOrderAndReturnsTotals() {
        var orderRepository = mock(CollectionOrderRepository.class);
        var userRepository = mock(UserRepository.class);
        var user = new User();
        user.setId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(CollectionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new CollectionOrderService(orderRepository, userRepository);
        var request = new CreateCollectionOrderRequest(9L, List.of(
                new CreatePacketRequest(java.util.Map.of(ContainerType.PET, 2, ContainerType.BRIK, 1,
                        ContainerType.MEZCLADO, 3)),
                new CreatePacketRequest(java.util.Map.of(ContainerType.VIDRIO, 8)),
                new CreatePacketRequest(java.util.Map.of(ContainerType.LATA, 5))
        ));

        var response = service.create(request);

        assertThat(response.totalBags()).isEqualTo(3);
        assertThat(response.estimatedContainerTotal()).isEqualTo(19);
        assertThat(response.packets()).hasSize(3);

        var orderCaptor = ArgumentCaptor.forClass(CollectionOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        var persistedOrder = orderCaptor.getValue();
        assertThat(persistedOrder.getPackets()).hasSize(3)
                .allMatch(packet -> packet.getCollection() == persistedOrder);
    }
}
