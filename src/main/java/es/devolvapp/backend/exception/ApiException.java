package es.devolvapp.backend.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        this(status, code, message, fieldErrors, null);
    }

    private ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors,
                         Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException fieldError(String code, String message, String field, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of(field, detail));
    }
}
