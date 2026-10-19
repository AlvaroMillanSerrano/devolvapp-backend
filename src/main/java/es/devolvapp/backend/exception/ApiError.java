package es.devolvapp.backend.exception;

import java.util.Map;

public record ApiError(int status, String code, String message, Map<String, String> fieldErrors) {
}
