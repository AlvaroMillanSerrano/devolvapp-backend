package es.devolvapp.backend.messaging;

import java.time.Instant;

public record DevolvMessage(String message, Instant sentAt) {
}
