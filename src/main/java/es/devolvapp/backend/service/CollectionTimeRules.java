package es.devolvapp.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

final class CollectionTimeRules {
    private CollectionTimeRules() {
    }

    static boolean hasNotEnded(LocalDate collectionDate, LocalTime endTime, LocalDateTime now) {
        if (collectionDate == null || endTime == null) return false;
        return collectionDate.isAfter(now.toLocalDate())
                || (collectionDate.equals(now.toLocalDate()) && endTime.isAfter(now.toLocalTime()));
    }

    static boolean canStartToday(LocalDate collectionDate, LocalDate today) {
        return collectionDate != null && collectionDate.equals(today);
    }
}
