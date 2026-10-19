package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record PagarLoteRequest(@NotEmpty List<Long> ids) {}
