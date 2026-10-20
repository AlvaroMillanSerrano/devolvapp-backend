package es.devolvapp.backend.dto.admin;

import es.devolvapp.backend.entity.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ActualizarRolesRequest(@NotEmpty List<@NotNull Role> roles) {}
