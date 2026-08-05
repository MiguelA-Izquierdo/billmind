package dev.izquierdo.billmind._shared.infrastructure.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DependencyHealthProbeTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private ObjectProvider<DataSource> dataSourceProvider;

    @Mock
    private Connection connection;

    @Mock
    private ObjectProvider<KafkaHealthIndicator> kafkaHealthProvider;

    @Mock
    private KafkaHealthIndicator kafkaHealthIndicator;

    private DependencyHealthProbe probe;

    @BeforeEach
    void setUp() {
        when(dataSourceProvider.getIfAvailable()).thenReturn(dataSource);
        probe = new DependencyHealthProbe(dataSourceProvider, kafkaHealthProvider);
    }

    /** A context without JPA still has to build; the probe answers "down" instead of failing startup. */
    @Test
    void shouldReportDownWhenNoDataSourceIsConfigured() {
        when(dataSourceProvider.getIfAvailable()).thenReturn(null);
        givenKafka(Health.up().build());

        assertThat(probe.dependenciesUp()).isFalse();
    }

    @Test
    void shouldReportUpWhenDatabaseAndKafkaAnswer() throws SQLException {
        givenDatabase(true);
        givenKafka(Health.up().build());

        assertThat(probe.dependenciesUp()).isTrue();
    }

    @Test
    void shouldReportDownWhenDatabaseConnectionFails() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        givenKafka(Health.up().build());

        assertThat(probe.dependenciesUp()).isFalse();
    }

    @Test
    void shouldReportDownWhenDatabaseConnectionIsNotValid() throws SQLException {
        givenDatabase(false);
        givenKafka(Health.up().build());

        assertThat(probe.dependenciesUp()).isFalse();
    }

    @Test
    void shouldReportDownWhenKafkaIsUnreachable() throws SQLException {
        givenDatabase(true);
        givenKafka(Health.down().build());

        assertThat(probe.dependenciesUp()).isFalse();
    }

    /** {@code kafka.enabled=false} leaves no indicator bean: a broker nobody talks to cannot be down. */
    @Test
    void shouldIgnoreKafkaWhenTheIndicatorIsNotRegistered() throws SQLException {
        givenDatabase(true);
        when(kafkaHealthProvider.getIfAvailable()).thenReturn(null);

        assertThat(probe.dependenciesUp()).isTrue();
        verify(kafkaHealthIndicator, never()).health();
    }

    /** The endpoint is unauthenticated and unmetered — the cache is what bounds its cost. */
    @Test
    void shouldProbeDependenciesOnlyOnceWithinTheCacheWindow() throws SQLException {
        givenDatabase(true);
        givenKafka(Health.up().build());

        for (int i = 0; i < 20; i++) {
            assertThat(probe.dependenciesUp()).isTrue();
        }

        verify(dataSource, times(1)).getConnection();
        verify(kafkaHealthIndicator, times(1)).health();
    }

    /** Kafka is never asked when the database already answered no. */
    @Test
    void shouldNotTouchKafkaWhenTheDatabaseIsAlreadyDown() throws SQLException {
        givenDatabase(false);
        givenKafka(Health.up().build());

        assertThat(probe.dependenciesUp()).isFalse();
        verify(kafkaHealthIndicator, never()).health();
    }

    /**
     * A dead Postgres blocks in {@code getConnection()} for the pool's connection-timeout (30s by
     * default), which the validation timeout never bounds. The caller must get its 503 long before that.
     */
    @Test
    void shouldGiveUpOnADatabaseThatNeverAnswers() throws SQLException {
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            Thread.sleep(30_000);
            return connection;
        });
        givenKafka(Health.up().build());

        long startedAt = System.nanoTime();
        boolean up = probe.dependenciesUp();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(up).isFalse();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    private void givenDatabase(boolean valid) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(org.mockito.ArgumentMatchers.anyInt())).thenReturn(valid);
    }

    private void givenKafka(Health health) {
        when(kafkaHealthProvider.getIfAvailable()).thenReturn(kafkaHealthIndicator);
        when(kafkaHealthIndicator.health()).thenReturn(health);
    }
}