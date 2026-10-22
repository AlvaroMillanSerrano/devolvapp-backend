package es.devolvapp.backend.messaging;

import java.time.Instant;

public record DevolvMessage(String email, String type, String message, Instant sentAt) {
}
