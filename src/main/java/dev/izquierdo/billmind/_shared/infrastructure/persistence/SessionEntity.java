package dev.izquierdo.billmind._shared.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected SessionEntity() {}

    public static SessionEntity create(UUID id) {
        SessionEntity entity = new SessionEntity();
        entity.id          = id;
        entity.firstSeenAt = Instant.now();
        entity.lastSeenAt  = entity.firstSeenAt;
        return entity;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public UUID getId()            { return id; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt()  { return lastSeenAt; }
}