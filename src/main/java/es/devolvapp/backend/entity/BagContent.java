package es.devolvapp.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bag_content", uniqueConstraints = @UniqueConstraint(
        name = "bag_content_bag_id_container_type_code_key",
        columnNames = {"bag_id", "container_type_code"}))
@Getter
@Setter
@NoArgsConstructor
public class BagContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id", nullable = false)
    private Bag bag;

    @Column(name = "container_type_code", nullable = false, length = 20)
    private String containerTypeCode;

    @Positive
    @Column(nullable = false)
    private Short quantity;
}
