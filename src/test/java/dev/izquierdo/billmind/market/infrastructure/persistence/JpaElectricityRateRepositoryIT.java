package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Exercises the real JPQL of findLatestPerTariff against Postgres — the expiry filter lives in the
// query string, so a mocked repository cannot cover it. findAll() keeps returning expired rows:
// it feeds the admin history, which is the one place expired rates must stay visible.
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaElectricityRateRepositoryIT {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.host",     postgres::getHost);
        registry.add("spring.datasource.port",     () -> postgres.getMappedPort(5432).toString());
        registry.add("spring.datasource.database", postgres::getDatabaseName);
    }

    @MockitoBean StartupReadinessChecker startupReadinessChecker;
    @MockitoBean InvoiceFieldExtractor   invoiceFieldExtractor;

    @Autowired JpaElectricityRateRepository repository;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void clearCorpus() {
        repository.deleteAll();
    }

    @Test
    void shouldIncludeRateWhenValidToIsInTheFuture() {
        save("IBERDROLA", "2.0TD", TODAY.minusDays(30), TODAY.plusDays(1));

        assertThat(companiesInCurrentCorpus()).containsExactly("IBERDROLA");
    }

    @Test
    void shouldIncludeRateWhenValidToIsNull() {
        save("ENDESA", "2.0TD", TODAY.minusDays(30), null);

        assertThat(companiesInCurrentCorpus()).containsExactly("ENDESA");
    }

    @Test
    void shouldIncludeRateWhenValidToIsExactlyToday() {
        // validTo is inclusive: a rate valid "until today" is still current today.
        save("NATURGY", "2.0TD", TODAY.minusDays(30), TODAY);

        assertThat(companiesInCurrentCorpus()).containsExactly("NATURGY");
    }

    @Test
    void shouldExcludeRateWhenValidToHasPassed() {
        save("REPSOL", "2.0TD", TODAY.minusDays(30), TODAY.minusDays(1));

        assertThat(repository.findLatestPerTariff()).isEmpty();
    }

    @Test
    void shouldDropTariffEntirelyWhenItsNewestEntryHasExpired() {
        // The newest entry supersedes the open-ended older one. Once it expires the tariff has no
        // current price — the older row must not be resurrected.
        save("TOTALENERGIES", "2.0TD", TODAY.minusDays(60), null);
        save("TOTALENERGIES", "2.0TD", TODAY.minusDays(30), TODAY.minusDays(1));

        assertThat(repository.findLatestPerTariff()).isEmpty();
    }

    @Test
    void shouldKeepExpiredRatesInFindAllForTheHistory() {
        save("REPSOL", "2.0TD", TODAY.minusDays(30), TODAY.minusDays(1));
        save("ENDESA", "2.0TD", TODAY.minusDays(30), null);

        assertThat(repository.findAll()).hasSize(2);
        assertThat(companiesInCurrentCorpus()).containsExactly("ENDESA");
    }

    @Test
    void shouldSeparateExpiredAndCurrentTariffsOfTheSameCompany() {
        save("IBERDROLA", "2.0TD",      TODAY.minusDays(30), TODAY.plusDays(10));
        save("IBERDROLA", "Plan Estable", TODAY.minusDays(30), TODAY.minusDays(2));

        assertThat(repository.findLatestPerTariff())
                .extracting(ElectricityRate::getTariffName)
                .containsExactly("2.0TD");
    }

    private List<String> companiesInCurrentCorpus() {
        return repository.findLatestPerTariff().stream().map(ElectricityRate::getCompany).toList();
    }

    private void save(String company, String tariffName, LocalDate validFrom, LocalDate validTo) {
        repository.save(ElectricityRate.builder(UUID.randomUUID())
                .supplyType(SupplyDomain.ELECTRICITY)
                .company(company)
                .tariffName(tariffName)
                .pricePerKwh(new BigDecimal("0.150000"))
                .validFrom(validFrom)
                .validTo(validTo)
                .source("REE")
                .build());
    }
}