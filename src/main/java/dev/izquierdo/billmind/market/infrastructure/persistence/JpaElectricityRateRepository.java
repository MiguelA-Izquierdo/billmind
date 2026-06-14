package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaElectricityRateRepository implements ElectricityRateRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaElectricityRateRepository.class);

    private final ElectricityRateJpaRepository jpa;

    public JpaElectricityRateRepository(ElectricityRateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(ElectricityRate rate) {
        try {
            jpa.save(ElectricityRateMapper.toEntity(rate));
            log.debug("Market rate saved: company={} tariff={} validFrom={}",
                rate.getCompany(), rate.getTariffName(), rate.getValidFrom());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Market rate already persisted, skipping duplicate: id={}", rate.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElectricityRate> findAll() {
        return jpa.findAll().stream().map(ElectricityRateMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElectricityRate> findLatestPerTariff() {
        return jpa.findLatestPerTariff().stream().map(ElectricityRateMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteAll() {
        long count = jpa.count();
        jpa.deleteAll();
        log.info("All market rates deleted: {} records removed", count);
    }
}