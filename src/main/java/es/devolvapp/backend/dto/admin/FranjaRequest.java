package es.devolvapp.backend.dto.admin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;
public record FranjaRequest(@Min(1) @Max(7) int diaSemana, @NotNull LocalTime horaInicio,
                            @NotNull LocalTime horaFin, @Positive int capacidad) {}
