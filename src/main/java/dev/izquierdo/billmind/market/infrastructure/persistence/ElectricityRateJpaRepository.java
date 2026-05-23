package dev.izquierdo.billmind.market.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ElectricityRateJpaRepository extends JpaRepository<ElectricityRateEntity, UUID> {
}