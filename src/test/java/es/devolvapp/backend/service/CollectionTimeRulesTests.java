package es.devolvapp.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionTimeRulesTests {

    @Test
    void onlyRejectsSameDaySlotsAfterTheirEndTime() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 13, 0);
        LocalDate today = now.toLocalDate();

        assertThat(CollectionTimeRules.hasNotEnded(today, LocalTime.of(12, 59, 59), now)).isFalse();
        assertThat(CollectionTimeRules.hasNotEnded(today, LocalTime.of(13, 0), now)).isFalse();
        assertThat(CollectionTimeRules.hasNotEnded(today, LocalTime.of(13, 0, 1), now)).isTrue();
        assertThat(CollectionTimeRules.hasNotEnded(today.plusDays(1), LocalTime.NOON, now)).isTrue();
        assertThat(CollectionTimeRules.hasNotEnded(today.minusDays(1), LocalTime.MAX, now)).isFalse();
    }

    @Test
    void onlyAllowsStartingOnToday() {
        LocalDate today = LocalDate.of(2026, 10, 21);

        assertThat(CollectionTimeRules.canStartToday(today, today)).isTrue();
        assertThat(CollectionTimeRules.canStartToday(today.minusDays(1), today)).isFalse();
        assertThat(CollectionTimeRules.canStartToday(today.plusDays(1), today)).isFalse();
        assertThat(CollectionTimeRules.canStartToday(null, today)).isFalse();
    }
}
