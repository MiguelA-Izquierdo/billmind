package dev.izquierdo.billmind._shared.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEntityTest {

    @Test
    void shouldSetIdAndTimestampsOnCreate() {
        UUID id = UUID.randomUUID();

        SessionEntity session = SessionEntity.create(id);

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getFirstSeenAt()).isNotNull();
        assertThat(session.getLastSeenAt()).isEqualTo(session.getFirstSeenAt());
    }

    @Test
    void shouldUpdateLastSeenAtOnTouch() throws InterruptedException {
        SessionEntity session = SessionEntity.create(UUID.randomUUID());
        Instant before = session.getLastSeenAt();
        Thread.sleep(2);

        session.touch();

        assertThat(session.getLastSeenAt()).isAfter(before);
    }

    @Test
    void shouldNotChangeFirstSeenAtOnTouch() throws InterruptedException {
        SessionEntity session = SessionEntity.create(UUID.randomUUID());
        Instant firstSeen = session.getFirstSeenAt();
        Thread.sleep(2);

        session.touch();

        assertThat(session.getFirstSeenAt()).isEqualTo(firstSeen);
    }
}