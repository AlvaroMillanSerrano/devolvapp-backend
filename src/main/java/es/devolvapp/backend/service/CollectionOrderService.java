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
import es.devolvapp.backend.exception.ApiException;
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

@Service
@RequiredArgsConstructor
public class CollectionOrderService {

    private final CollectionOrderRepository collectionOrderRepository;
    private final ContainerCatalogRepository containerCatalogRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CodeGenerator codeGenerator;

    @Transactional
    public CollectionOrderResponse create(CreateCollectionRequest request, Long citizenId) {
        User citizen = userRepository.findById(citizenId)
                .orElseThrow(() -> ApiException.notFound("Ciudadano no encontrado"));
        if (!"ACTIVA".equals(citizen.getStatus())) {
            throw ApiException.forbidden("El usuario no está activo");
        }
        if (!citizen.getRoles().contains(Role.CIUDADANO)) {
            throw ApiException.forbidden("El usuario no tiene el rol de ciudadano");
        }

        AddressData address = findAddress(citizen.getId());
        boolean hasScheduleData = request.fecha() != null || request.franjaId() != null;
        if (hasScheduleData && (request.fecha() == null || request.franjaId() == null)) {
            throw ApiException.fieldError("VALIDATION_ERROR", "La fecha y la franja deben enviarse juntas",
                    "fecha", "Indica fecha y franja, o deja ambos campos vacíos para programar más adelante");
        }
        TimeSlotData slot = null;
        if (hasScheduleData) {
            slot = findAndLockTimeSlot(request.franjaId());
            if (!slot.active()) {
                throw ApiException.conflict("FRANJA_COMPLETA", "La franja horaria no está activa");
            }
            if (request.fecha().getDayOfWeek().getValue() != slot.dayOfWeek()) {
                throw ApiException.fieldError("VALIDATION_ERROR", "La fecha no corresponde a la franja seleccionada",
                        "fecha", "La fecha no corresponde al día de la semana de la franja seleccionada");
            }
            if (address.zoneId() == null || !address.zoneId().equals(slot.zoneId())) {
                throw ApiException.unprocessable("ZONA_SIN_SERVICIO",
                        "La franja seleccionada no corresponde a la zona del domicilio");
            }
            validateSlotCapacity(request, slot);
        }
        validateBagCount(request.bolsas().size());

        var collection = new CollectionOrder();
        collection.setCode(codeGenerator.next("seq_collection_code", "P-", 10, 4));
        collection.setCitizen(citizen);
        // Sin fecha/franja el pedido queda pendiente de programación (MVP).
        collection.setZoneId(slot == null ? null : slot.zoneId());
        collection.setCollectorId(slot == null ? null : findCollector(slot.zoneId(), slot.dayOfWeek()));
        collection.setTimeSlotId(slot == null ? null : slot.id());
        collection.setCollectionDate(slot == null ? null : request.fecha());
        collection.setStartTime(slot == null ? null : slot.startTime());
        collection.setEndTime(slot == null ? null : slot.endTime());
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
            throw ApiException.fieldError("VALIDATION_ERROR", "Se supera la capacidad de la bolsa",
                    "bolsas.contenidos." + type, "La capacidad máxima para " + type + " es "
                            + catalog.getMaxBagCapacity() + " envases");
            }
            totalQuantity += quantity;
            totalCapacity += catalog.getMaxBagCapacity();
        }

        if (totalQuantity > Short.MAX_VALUE || totalCapacity > Short.MAX_VALUE) {
            throw ApiException.fieldError("VALIDATION_ERROR", "La bolsa supera la capacidad máxima",
                    "bolsas.contenidos", "La cantidad total supera la capacidad máxima");
        }

        var bag = new Bag();
        bag.setCode(codeGenerator.next("seq_bag_code", "B-", 36, 5));
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
                FROM time_slot WHERE id = ? FOR UPDATE
                """, (rs, rowNum) -> new TimeSlotData(
                rs.getLong("id"), rs.getLong("zone_id"), rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getInt("capacity"), rs.getBoolean("active")
        ), timeSlotId);
        if (matches.isEmpty()) throw ApiException.notFound("Franja horaria no encontrada");
        return matches.getFirst();
    }

    private AddressData findAddress(Long citizenId) {
        List<AddressData> matches = jdbcTemplate.query("""
                SELECT a.street_type, a.street_name, a.street_number, a.floor, a.letter,
                       RTRIM(a.zip_code) AS zip_code, a.city, a.lat, a.lng, z.zone_id
                FROM address a LEFT JOIN zip_code z ON z.code = a.zip_code
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
            throw ApiException.unprocessable("DOMICILIO_REQUERIDO", "Completa un domicilio válido antes de crear un pedido");
        }
        return matches.getFirst();
    }

    private void validateSlotCapacity(CreateCollectionRequest request, TimeSlotData slot) {
        Long reserved = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM collection
                WHERE time_slot_id = ? AND collection_date = ? AND status <> 'CANCELADA'
                """, Long.class, slot.id(), request.fecha());
        if (reserved != null && reserved >= slot.capacity()) {
            throw ApiException.conflict("FRANJA_COMPLETA", "La franja horaria ya no tiene capacidad");
        }
    }

    private void validateBagCount(int bagCount) {
        List<String> configuredMax = jdbcTemplate.query("""
                SELECT param_value FROM app_parameter WHERE param_key = 'max_bolsas_pedido'
                """, (rs, rowNum) -> rs.getString("param_value"));
        if (!configuredMax.isEmpty() && bagCount > Integer.parseInt(configuredMax.getFirst())) {
            throw ApiException.fieldError("VALIDATION_ERROR", "Se supera el máximo de bolsas",
                    "bolsas", "El máximo de bolsas por recogida es " + configuredMax.getFirst());
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private Long findCollector(Long zoneId, int dayOfWeek) {
        List<Long> collectors = jdbcTemplate.query("""
                SELECT collector_id FROM zone_day_collector WHERE zone_id = ? AND day_of_week = ?
                """, (rs, rowNum) -> rs.getLong("collector_id"), zoneId, dayOfWeek);
        return collectors.isEmpty() ? null : collectors.getFirst();
    }

    private record TimeSlotData(Long id, Long zoneId, int dayOfWeek,
                                java.time.LocalTime startTime, java.time.LocalTime endTime,
                                int capacity, boolean active) {}

    private record AddressData(String streetType, String streetName, String streetNumber,
                               String floor, String letter, String zipCode, String city,
                               Double latitude, Double longitude, Long zoneId) {
    }
}
