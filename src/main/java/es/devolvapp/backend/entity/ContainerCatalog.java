package es.devolvapp.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "container_type")
@Getter
@Setter
@NoArgsConstructor
public class ContainerCatalog {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 40)
    private String icon;

    @Column(name = "max_bag_capacity", nullable = false)
    private Short maxBagCapacity;

    @Column(name = "co2_kg_per_container", nullable = false, precision = 6, scale = 3)
    private BigDecimal co2KgPerContainer;
}
