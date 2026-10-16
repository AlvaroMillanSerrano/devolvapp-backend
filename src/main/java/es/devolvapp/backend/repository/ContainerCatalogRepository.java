package es.devolvapp.backend.repository;

import es.devolvapp.backend.entity.ContainerCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerCatalogRepository extends JpaRepository<ContainerCatalog, String> {
}
