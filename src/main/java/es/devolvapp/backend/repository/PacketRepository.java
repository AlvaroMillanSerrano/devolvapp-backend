package es.devolvapp.backend.repository;

import es.devolvapp.backend.entity.Packet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacketRepository extends JpaRepository<Packet, Long> {
}
