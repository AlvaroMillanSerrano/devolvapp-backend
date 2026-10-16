package es.devolvapp.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bag")
@Getter
@Setter
@NoArgsConstructor
public class Bag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private CollectionOrder collection;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Positive
    @Column(nullable = false)
    private Short quantity;

    @Positive
    @Column(name = "max_capacity", nullable = false)
    private Short maxCapacity;

    @Column(length = 14)
    private String result;

    @Column(name = "valid_containers")
    private Short validContainers;

    private String note;

    @NotEmpty
    @OneToMany(mappedBy = "bag", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BagContent> contents = new ArrayList<>();

    public void addContent(BagContent content) {
        contents.add(content);
        content.setBag(this);
    }
}
