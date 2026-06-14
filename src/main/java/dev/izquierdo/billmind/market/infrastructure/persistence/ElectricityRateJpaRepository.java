package dev.izquierdo.billmind.market.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ElectricityRateJpaRepository extends JpaRepository<ElectricityRateEntity, UUID> {

    @Query("""
            SELECT e FROM ElectricityRateEntity e
            WHERE e.validFrom = (
                SELECT MAX(e2.validFrom) FROM ElectricityRateEntity e2
                WHERE e2.company = e.company AND e2.tariffName = e.tariffName
            )
            """)
    List<ElectricityRateEntity> findLatestPerTariff();
}