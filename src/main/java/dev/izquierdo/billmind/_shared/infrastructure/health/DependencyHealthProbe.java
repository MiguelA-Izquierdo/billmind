package dev.izquierdo.billmind._shared.infrastructure.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Answers a single yes/no question: are the dependencies this application cannot serve a request
 * without — PostgreSQL, plus Kafka when {@code kafka.enabled=true} — reachable right now. It reports
 * no names, no versions and no error text; that detail belongs to the Actuator health endpoint on the
 * internal management port, which is authenticated.
 *
 * <p>The answer is cached for a short window because the caller is unauthenticated and unmetered
 * (see {@code PingController}): without it, every request would open a database connection and build
 * a Kafka {@code AdminClient}, so hammering the public ping would cost more than the requests the
 * rate limiter guards. With it, the probe runs at most once per window no matter the traffic.
 */
@Component
public class DependencyHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DependencyHealthProbe.class);

    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;

    /**
     * Caps the whole database check, not just the validation query. When Postgres is gone the wait
     * happens in {@code getConnection()} — the pool's own {@code connection-timeout}, 30s by default —
     * which {@code isValid} never gets to bound. Without this cap a monitor times out on a probe that
     * already knows the answer instead of reading the 503.
     */
    private static final Duration DATABASE_PROBE_TIMEOUT = Duration.ofSeconds(2);

    /** Shorter than any sane monitor interval, so a caller never reads a verdict it could call stale. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    /**
     * Resolved lazily, never injected: a probe that reports health must not be the reason the context
     * fails to build. Without a {@code DataSource} there is nothing to serve, so it answers "down".
     */
    private final ObjectProvider<DataSource> dataSource;
    private final ObjectProvider<KafkaHealthIndicator> kafkaHealthIndicator;

    private final AtomicReference<Probe> lastProbe = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final ExecutorService databaseProbeExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ping-db-probe");
        thread.setDaemon(true);
        return thread;
    });

    public DependencyHealthProbe(ObjectProvider<DataSource> dataSource,
                                 ObjectProvider<KafkaHealthIndicator> kafkaHealthIndicator) {
        this.dataSource = dataSource;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
    }

    @PreDestroy
    void shutdown() {
        databaseProbeExecutor.shutdownNow();
    }

    public boolean dependenciesUp() {
        Probe snapshot = lastProbe.get();
        if (snapshot != null && snapshot.isFresh()) {
            return snapshot.up();
        }
        if (!acquireRefreshTurn(snapshot)) {
            return snapshot.up();
        }
        try {
            Probe latest = lastProbe.get();
            return latest != null && latest.isFresh() ? latest.up() : probe();
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Only one thread probes per window. A concurrent caller that already has an answer serves the
     * stale one rather than queueing behind a probe that may take seconds; the very first caller has
     * nothing to serve, so it waits.
     */
    private boolean acquireRefreshTurn(Probe snapshot) {
        if (snapshot == null) {
            refreshLock.lock();
            return true;
        }
        return refreshLock.tryLock();
    }

    private boolean probe() {
        boolean up = databaseUp() && kafkaUp();
        lastProbe.set(new Probe(up, System.nanoTime()));
        return up;
    }

    /**
     * The probe runs on its own thread so the answer is bounded even when the pool is not: a caller
     * waits {@link #DATABASE_PROBE_TIMEOUT} at most. An abandoned probe cannot pile up — the cached
     * {@code false} keeps the next one from starting for a whole window.
     */
    private boolean databaseUp() {
        Future<Boolean> probe = databaseProbeExecutor.submit(this::queryDatabase);
        try {
            return probe.get(DATABASE_PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            probe.cancel(true);
            log.warn("Ping probe: database did not answer within {}s", DATABASE_PROBE_TIMEOUT.toSeconds());
            return false;
        } catch (ExecutionException e) {
            log.warn("Ping probe: database check failed ({})", e.getCause().getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean queryDatabase() {
        DataSource resolved = dataSource.getIfAvailable();
        if (resolved == null) {
            log.warn("Ping probe: no DataSource is configured in this context");
            return false;
        }
        try (Connection connection = resolved.getConnection()) {
            return connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Ping probe: database is not reachable ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /** No indicator means {@code kafka.enabled=false}: Kafka is not part of this deployment. */
    private boolean kafkaUp() {
        KafkaHealthIndicator indicator = kafkaHealthIndicator.getIfAvailable();
        if (indicator == null) {
            return true;
        }
        boolean up = Status.UP.equals(indicator.health().getStatus());
        if (!up) {
            log.warn("Ping probe: Kafka is not reachable");
        }
        return up;
    }

    private record Probe(boolean up, long takenAtNanos) {
        boolean isFresh() {
            return System.nanoTime() - takenAtNanos < CACHE_TTL.toNanos();
        }
    }
}