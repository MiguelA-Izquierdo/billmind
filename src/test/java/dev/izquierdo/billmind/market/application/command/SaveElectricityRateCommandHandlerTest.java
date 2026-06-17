package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.market.application.usecase.SaveElectricityRateUseCase;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaveElectricityRateCommandHandlerTest {

    @Mock
    private SaveElectricityRateUseCase saveElectricityRateUseCase;

    @InjectMocks
    private SaveElectricityRateCommandHandler handler;

    @Test
    void shouldReturnCorrectCommandType() {
        assertEquals(SaveElectricityRateCommand.class, handler.commandType());
    }

    @Test
    void shouldBuildElectricityRateFromCommandAndDelegate() {
        UUID id = UUID.randomUUID();
        LocalDate validFrom = LocalDate.of(2025, 1, 1);
        LocalDate validTo = LocalDate.of(2025, 12, 31);
        Instant receivedAt = Instant.parse("2025-01-01T00:00:00Z");
        BigDecimal pricePerKwh = new BigDecimal("0.150000");
        BigDecimal valle = new BigDecimal("0.100000");
        BigDecimal llano = new BigDecimal("0.130000");
        BigDecimal punta = new BigDecimal("0.180000");
        BigDecimal powerPrice = new BigDecimal("0.040000");
        BigDecimal powerPriceP2 = new BigDecimal("0.035000");

        SaveElectricityRateCommand command = new SaveElectricityRateCommand(
            id, SupplyDomain.ELECTRICITY, "IBERDROLA", "2.0TD",
            pricePerKwh, valle, llano, punta,
            powerPrice, powerPriceP2,
            validFrom, validTo, "ES-MD", "REE", receivedAt
        );

        handler.handle(command);

        ArgumentCaptor<ElectricityRate> captor = ArgumentCaptor.forClass(ElectricityRate.class);
        verify(saveElectricityRateUseCase).execute(captor.capture());

        ElectricityRate saved = captor.getValue();
        assertEquals(id,              saved.getId());
        assertEquals(SupplyDomain.ELECTRICITY, saved.getSupplyType());
        assertEquals("IBERDROLA",     saved.getCompany());
        assertEquals("2.0TD",         saved.getTariffName());
        assertEquals(pricePerKwh,     saved.getPricePerKwh());
        assertEquals(valle,           saved.getPricePerKwhValle());
        assertEquals(llano,           saved.getPricePerKwhLlano());
        assertEquals(punta,           saved.getPricePerKwhPunta());
        assertEquals(powerPrice,      saved.getContractedPowerPrice());
        assertEquals(powerPriceP2,    saved.getContractedPowerPriceP2());
        assertEquals(validFrom,       saved.getValidFrom());
        assertEquals(validTo,         saved.getValidTo());
        assertEquals("ES-MD",         saved.getRegion());
        assertEquals("REE",           saved.getSource());
        assertEquals(receivedAt,      saved.getReceivedAt());
    }

    @Test
    void shouldDelegateWithOptionalFieldsNull() {
        UUID id = UUID.randomUUID();
        SaveElectricityRateCommand command = new SaveElectricityRateCommand(
            id, SupplyDomain.GAS, "NATURGY", "Tarifa Gas",
            null, new BigDecimal("0.050000"), null, null,
            null, null,
            LocalDate.of(2025, 6, 1), null, null, "CNMC", null
        );

        handler.handle(command);

        ArgumentCaptor<ElectricityRate> captor = ArgumentCaptor.forClass(ElectricityRate.class);
        verify(saveElectricityRateUseCase).execute(captor.capture());

        ElectricityRate saved = captor.getValue();
        assertEquals(id,              saved.getId());
        assertEquals(SupplyDomain.GAS, saved.getSupplyType());
        assertEquals("NATURGY",       saved.getCompany());
    }

    @Test
    void shouldRejectNullUseCase() {
        assertThrows(NullPointerException.class, () -> new SaveElectricityRateCommandHandler(null));
    }
}