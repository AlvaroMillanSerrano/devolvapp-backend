package es.devolvapp.backend.dto.auth;

import es.devolvapp.backend.dto.common.UsuarioResponse;

public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                            UsuarioResponse usuario) {
}
