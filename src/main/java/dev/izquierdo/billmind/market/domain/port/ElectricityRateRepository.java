package dev.izquierdo.billmind.market.domain.port;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;

import java.util.List;

public interface ElectricityRateRepository {

    void save(ElectricityRate marketRate);

    List<ElectricityRate> findAll();

    List<ElectricityRate> findLatestPerTariff();

    void deleteAll();
}