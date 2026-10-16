package es.devolvapp.backend;

import es.devolvapp.backend.dto.CreateCollectionOrderRequest;
import es.devolvapp.backend.dto.CreatePacketRequest;
import es.devolvapp.backend.entity.ContainerType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionOrderValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsOrderWithSeveralValidBags() {
        var request = new CreateCollectionOrderRequest(1L, List.of(
                new CreatePacketRequest(java.util.Map.of(ContainerType.PET, 2, ContainerType.BRIK, 1,
                        ContainerType.MEZCLADO, 3)),
                new CreatePacketRequest(java.util.Map.of(ContainerType.VIDRIO, 8))
        ));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingTypeAndNonPositiveQuantities() {
        var request = new CreateCollectionOrderRequest(1L, List.of(
                new CreatePacketRequest(java.util.Map.of(ContainerType.PET, 0)),
                new CreatePacketRequest(java.util.Map.of(ContainerType.BRIK, -2)),
                new CreatePacketRequest(java.util.Map.of())
        ));

        var violations = validator.validate(request);

        assertThat(violations).hasSize(3);
        assertThat(violations).extracting(violation -> violation.getMessage())
                .contains("La cantidad estimada por tipo debe ser mayor que cero",
                        "La bolsa debe incluir al menos un tipo de envase");
    }
}
