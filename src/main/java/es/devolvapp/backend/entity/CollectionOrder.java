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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "collection")
@Getter
@Setter
@NoArgsConstructor
public class CollectionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "collector_id")
    private Long collectorId;

    @Column(name = "time_slot_id")
    private Long timeSlotId;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "addr_street_type", nullable = false, length = 20)
    private String addressStreetType;

    @Column(name = "addr_street_name", nullable = false, length = 120)
    private String addressStreetName;

    @Column(name = "addr_street_number", nullable = false, length = 10)
    private String addressStreetNumber;

    @Column(name = "addr_floor", length = 10)
    private String addressFloor;

    @Column(name = "addr_letter", length = 10)
    private String addressLetter;

    @Column(name = "addr_zip_code", nullable = false, length = 5)
    private String addressZipCode;

    @Column(name = "addr_city", nullable = false, length = 80)
    private String addressCity;

    @Column(name = "addr_lat")
    private Double addressLatitude;

    @Column(name = "addr_lng")
    private Double addressLongitude;

    @Column(nullable = false, length = 12)
    private String status = "PENDIENTE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Bag> bags = new ArrayList<>();

    public void addBag(Bag bag) {
        bags.add(bag);
        bag.setCollection(this);
    }
}
