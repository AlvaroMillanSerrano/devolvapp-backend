package es.devolvapp.backend.repository;

import es.devolvapp.backend.entity.CollectionOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionOrderRepository extends JpaRepository<CollectionOrder, Long> {
}
