package es.devolvapp.backend.security;

import es.devolvapp.backend.entity.Role;
import java.util.Set;
public record AuthUser(Long id, String email, Set<Role> roles) {}
