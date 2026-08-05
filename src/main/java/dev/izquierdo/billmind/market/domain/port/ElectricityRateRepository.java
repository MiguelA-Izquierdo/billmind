package dev.izquierdo.billmind.market.domain.port;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;

import java.util.List;

public interface ElectricityRateRepository {

    void save(ElectricityRate marketRate);

    /** Every rate ever ingested, expired ones included — the history behind the admin listing. */
    List<ElectricityRate> findAll();

    /** The current rate per tariff. Expired rates (validTo before today) are excluded. */
    List<ElectricityRate> findLatestPerTariff();

    void deleteAll();
}