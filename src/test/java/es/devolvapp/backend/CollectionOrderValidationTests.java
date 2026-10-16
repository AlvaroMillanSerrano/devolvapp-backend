package es.devolvapp.backend;

import es.devolvapp.backend.dto.CreateBagRequest;
import es.devolvapp.backend.dto.CreateCollectionRequest;
import es.devolvapp.backend.entity.ContainerType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionOrderValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsFutureCollectionWithSeveralMixedBags() {
        var request = new CreateCollectionRequest(1L, 3L, LocalDate.now().plusDays(2), List.of(
                new CreateBagRequest(Map.of(ContainerType.PET, 2, ContainerType.BRIK, 1,
                        ContainerType.MEZCLADO, 3)),
                new CreateBagRequest(Map.of(ContainerType.VIDRIO, 8))
        ));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsNonPositiveQuantitiesAndEmptyBagContents() {
        var request = new CreateCollectionRequest(1L, 3L, LocalDate.now().plusDays(2), List.of(
                new CreateBagRequest(Map.of(ContainerType.PET, 0)),
                new CreateBagRequest(Map.of(ContainerType.BRIK, -2)),
                new CreateBagRequest(Map.of())
        ));

        var violations = validator.validate(request);

        assertThat(violations).hasSize(3);
        assertThat(violations).extracting(violation -> violation.getMessage())
                .contains("La cantidad por tipo debe ser mayor que cero",
                        "La bolsa debe incluir al menos un tipo de envase");
    }
}
