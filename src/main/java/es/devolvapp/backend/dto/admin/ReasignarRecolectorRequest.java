package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.NotNull;
public record ReasignarRecolectorRequest(@NotNull Long recolectorId) {}
