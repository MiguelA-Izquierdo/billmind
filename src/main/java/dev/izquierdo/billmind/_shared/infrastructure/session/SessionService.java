package dev.izquierdo.billmind._shared.infrastructure.session;

import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionEntity;
import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SessionService {

    private final SessionJpaRepository sessionJpaRepository;

    public SessionService(SessionJpaRepository sessionJpaRepository) {
        this.sessionJpaRepository = sessionJpaRepository;
    }

    @Transactional
    public void upsert(UUID sessionId) {
        sessionJpaRepository.findById(sessionId).ifPresentOrElse(
                entity -> {
                    entity.touch();
                    sessionJpaRepository.save(entity);
                },
                () -> sessionJpaRepository.save(SessionEntity.create(sessionId))
        );
    }
}