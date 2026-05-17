package dev.izquierdo.billmind._shared.infrastructure.session;

import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionEntity;
import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionJpaRepository sessionJpaRepository;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void shouldCreateNewSessionWhenNotExists() {
        UUID sessionId = UUID.randomUUID();
        when(sessionJpaRepository.findById(sessionId)).thenReturn(Optional.empty());

        sessionService.upsert(sessionId);

        ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(sessionId);
        assertThat(captor.getValue().getFirstSeenAt()).isNotNull();
    }

    @Test
    void shouldTouchExistingSessionAndSave() throws InterruptedException {
        UUID sessionId = UUID.randomUUID();
        SessionEntity existing = SessionEntity.create(sessionId);
        Instant originalLastSeen = existing.getLastSeenAt();
        Thread.sleep(2);
        when(sessionJpaRepository.findById(sessionId)).thenReturn(Optional.of(existing));

        sessionService.upsert(sessionId);

        verify(sessionJpaRepository).save(existing);
        assertThat(existing.getLastSeenAt()).isAfter(originalLastSeen);
    }

    @Test
    void shouldNotChangeFirstSeenAtOnTouch() {
        UUID sessionId = UUID.randomUUID();
        SessionEntity existing = SessionEntity.create(sessionId);
        Instant firstSeen = existing.getFirstSeenAt();
        when(sessionJpaRepository.findById(sessionId)).thenReturn(Optional.of(existing));

        sessionService.upsert(sessionId);

        assertThat(existing.getFirstSeenAt()).isEqualTo(firstSeen);
    }
}