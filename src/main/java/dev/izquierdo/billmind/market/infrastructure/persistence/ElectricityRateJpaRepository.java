package dev.izquierdo.billmind.market.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ElectricityRateJpaRepository extends JpaRepository<ElectricityRateEntity, UUID> {

    /**
     * The current rate per tariff: the newest entry of each (company, tariffName), dropped when it
     * has expired. {@code validTo} is inclusive and null means open-ended.
     * <p>
     * The subquery deliberately ranks over <em>all</em> rows, not just the unexpired ones: when the
     * newest entry of a tariff has expired, that tariff drops out entirely rather than resurrecting
     * an older row the producer already superseded. Expired rows stay queryable through
     * {@code findAll()}, which feeds the admin history.
     */
    @Query("""
            SELECT e FROM ElectricityRateEntity e
            WHERE e.validFrom = (
                SELECT MAX(e2.validFrom) FROM ElectricityRateEntity e2
                WHERE e2.company = e.company AND e2.tariffName = e.tariffName
            )
            AND (e.validTo IS NULL OR e.validTo >= :today)
            ORDER BY e.company, e.tariffName, e.receivedAt DESC
            """)
    List<ElectricityRateEntity> findLatestPerTariff(@Param("today") LocalDate today);
}