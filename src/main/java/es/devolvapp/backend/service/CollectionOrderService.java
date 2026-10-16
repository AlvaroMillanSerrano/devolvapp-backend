package es.devolvapp.backend.service;

import es.devolvapp.backend.dto.BagResponse;
import es.devolvapp.backend.dto.CollectionOrderResponse;
import es.devolvapp.backend.dto.CreateBagRequest;
import es.devolvapp.backend.dto.CreateCollectionRequest;
import es.devolvapp.backend.entity.Bag;
import es.devolvapp.backend.entity.BagContent;
import es.devolvapp.backend.entity.CollectionOrder;
import es.devolvapp.backend.entity.ContainerCatalog;
import es.devolvapp.backend.entity.ContainerType;
import es.devolvapp.backend.entity.Role;
import es.devolvapp.backend.entity.User;
import es.devolvapp.backend.repository.CollectionOrderRepository;
import es.devolvapp.backend.repository.ContainerCatalogRepository;
import es.devolvapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionOrderService {

    private final CollectionOrderRepository collectionOrderRepository;
    private final ContainerCatalogRepository containerCatalogRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public CollectionOrderResponse create(CreateCollectionRequest request) {
        User citizen = userRepository.findById(request.citizenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciudadano no encontrado"));
        if (!"ACTIVA".equals(citizen.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no está activo");
        }
        if (!citizen.getRoles().contains(Role.CIUDADANO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene el rol de ciudadano");
        }

        TimeSlotData slot = findAndLockTimeSlot(request.franjaId());
        if (!slot.active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La franja horaria no está activa");
        }
        if (request.fecha().getDayOfWeek().getValue() != slot.dayOfWeek()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha no corresponde al día de la semana de la franja seleccionada");
        }

        AddressData address = findAddress(citizen.getId());
        if (address.zoneId() == null || !address.zoneId().equals(slot.zoneId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La franja seleccionada no corresponde a la zona del domicilio");
        }
        validateSlotCapacity(request, slot);
        validateBagCount(request.bolsas().size());

        var collection = new CollectionOrder();
        collection.setCode("P-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase());
        collection.setCitizen(citizen);
        collection.setZoneId(slot.zoneId());
        collection.setCollectorId(findCollector(slot.zoneId(), slot.dayOfWeek()));
        collection.setTimeSlotId(slot.id());
        collection.setCollectionDate(request.fecha());
        collection.setStartTime(slot.startTime());
        collection.setEndTime(slot.endTime());
        collection.setAddressStreetType(address.streetType());
        collection.setAddressStreetName(address.streetName());
        collection.setAddressStreetNumber(address.streetNumber());
        collection.setAddressFloor(address.floor());
        collection.setAddressLetter(address.letter());
        collection.setAddressZipCode(address.zipCode());
        collection.setAddressCity(address.city());
        collection.setAddressLatitude(address.latitude());
        collection.setAddressLongitude(address.longitude());
        collection.setStatus("PENDIENTE");
        collection.setCreatedAt(OffsetDateTime.now());

        long estimatedTotal = 0;
        for (CreateBagRequest bagRequest : request.bolsas()) {
            Bag bag = createBag(bagRequest);
            estimatedTotal += bag.getQuantity();
            collection.addBag(bag);
        }

        CollectionOrder saved = collectionOrderRepository.save(collection);
        List<BagResponse> bagResponses = saved.getBags().stream()
                .map(this::toBagResponse)
                .toList();

        return new CollectionOrderResponse(
                saved.getId(),
                saved.getCode(),
                citizen.getId(),
                saved.getZoneId(),
                saved.getCreatedAt(),
                saved.getCollectionDate(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getStatus(),
                bagResponses.size(),
                estimatedTotal,
                bagResponses
        );
    }

    private Bag createBag(CreateBagRequest request) {
        int totalQuantity = 0;
        int totalCapacity = 0;
        for (Map.Entry<ContainerType, Integer> item : request.contenidos().entrySet()) {
            ContainerType type = item.getKey();
            int quantity = item.getValue();
            ContainerCatalog catalog = containerCatalogRepository.findById(type.name())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Tipo de envase no configurado: " + type));
            if (quantity > catalog.getMaxBagCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cantidad de " + type + " supera la capacidad permitida por bolsa");
            }
            totalQuantity += quantity;
            totalCapacity += catalog.getMaxBagCapacity();
        }

        if (totalQuantity > Short.MAX_VALUE || totalCapacity > Short.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La bolsa supera la capacidad máxima");
        }

        var bag = new Bag();
        bag.setCode("B-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase());
        bag.setQuantity((short) totalQuantity);
        bag.setMaxCapacity((short) totalCapacity);

        request.contenidos().forEach((type, quantity) -> {
            var content = new BagContent();
            content.setContainerTypeCode(type.name());
            content.setQuantity(quantity.shortValue());
            bag.addContent(content);
        });
        return bag;
    }

    private BagResponse toBagResponse(Bag bag) {
        var contents = new EnumMap<ContainerType, Integer>(ContainerType.class);
        for (BagContent content : bag.getContents()) {
            contents.put(ContainerType.valueOf(content.getContainerTypeCode()), (int) content.getQuantity());
        }
        return new BagResponse(bag.getId(), bag.getCode(), contents, bag.getQuantity(), bag.getMaxCapacity());
    }

    private TimeSlotData findAndLockTimeSlot(Long timeSlotId) {
        List<TimeSlotData> matches = jdbcTemplate.query("""
                SELECT id, zone_id, day_of_week, start_time, end_time, capacity, active
                FROM time_slot
                WHERE id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new TimeSlotData(
                rs.getLong("id"),
                rs.getLong("zone_id"),
                rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),
                rs.getInt("capacity"),
                rs.getBoolean("active")
        ), timeSlotId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Franja horaria no encontrada");
        }
        return matches.getFirst();
    }

    private AddressData findAddress(Long citizenId) {
        List<AddressData> matches = jdbcTemplate.query("""
                SELECT a.street_type, a.street_name, a.street_number, a.floor, a.letter,
                       RTRIM(a.zip_code) AS zip_code, a.city, a.lat, a.lng, z.zone_id
                FROM address a
                JOIN zip_code z ON z.code = a.zip_code
                WHERE a.user_id = ?
                """, (rs, rowNum) -> new AddressData(
                rs.getString("street_type"),
                rs.getString("street_name"),
                rs.getString("street_number"),
                rs.getString("floor"),
                rs.getString("letter"),
                rs.getString("zip_code"),
                rs.getString("city"),
                toDouble(rs.getBigDecimal("lat")),
                toDouble(rs.getBigDecimal("lng")),
                (Long) rs.getObject("zone_id")
        ), citizenId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El ciudadano no tiene un domicilio válido");
        }
        return matches.getFirst();
    }

    private void validateSlotCapacity(CreateCollectionRequest request, TimeSlotData slot) {
        Long reserved = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM collection
                WHERE time_slot_id = ? AND collection_date = ? AND status <> 'CANCELADA'
                """, Long.class, slot.id(), request.fecha());
        if (reserved != null && reserved >= slot.capacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La franja horaria ya no tiene capacidad");
        }
    }

    private void validateBagCount(int bagCount) {
        List<String> configuredMax = jdbcTemplate.query("""
                SELECT param_value FROM app_parameter WHERE param_key = 'max_bolsas_pedido'
                """, (rs, rowNum) -> rs.getString("param_value"));
        if (!configuredMax.isEmpty() && bagCount > Integer.parseInt(configuredMax.getFirst())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El máximo de bolsas por recogida es " + configuredMax.getFirst());
        }
    }

    private Long findCollector(Long zoneId, int dayOfWeek) {
        List<Long> collectors = jdbcTemplate.query("""
                SELECT collector_id FROM zone_day_collector WHERE zone_id = ? AND day_of_week = ?
                """, (rs, rowNum) -> rs.getLong("collector_id"), zoneId, dayOfWeek);
        return collectors.isEmpty() ? null : collectors.getFirst();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private record TimeSlotData(Long id, Long zoneId, int dayOfWeek,
                                java.time.LocalTime startTime, java.time.LocalTime endTime,
                                int capacity, boolean active) {
    }

    private record AddressData(String streetType, String streetName, String streetNumber,
                               String floor, String letter, String zipCode, String city,
                               Double latitude, Double longitude, Long zoneId) {
    }
}
