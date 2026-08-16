package com.autonomousapi.core.vehicle.marketvalue.fipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.VehicleStatus;
import com.autonomousapi.core.vehicle.VehicleType;
import com.autonomousapi.core.vehicle.marketvalue.VehicleMarketValue;
import com.autonomousapi.core.vehicle.marketvalue.VehicleMarketValueRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VehicleFipeSyncJobTest {

    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final VehicleMarketValueRepository marketValues = mock(VehicleMarketValueRepository.class);
    private final FipeClient fipeClient = mock(FipeClient.class);
    private final FipeMatchingService matching = new FipeMatchingService();

    private final VehicleFipeSyncJob job = new VehicleFipeSyncJob(vehicles, marketValues, fipeClient, matching);

    private static Vehicle veiculoComTipo(String plate, String brand, String model, int modelYear, VehicleType tipo) {
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), plate, brand, model, modelYear, 1000);
        vehicle.update(plate, brand, model, modelYear, 1000, VehicleStatus.ATIVO, tipo, null, null, Map.of());
        return vehicle;
    }

    @Test
    void parseValorConverteFormatoBrasileiroParaBigDecimal() {
        assertEquals(new BigDecimal("85608.00"), VehicleFipeSyncJob.parseValor("R$ 85.608,00"));
        assertEquals(new BigDecimal("999.00"), VehicleFipeSyncJob.parseValor("R$ 999,00"));
    }

    @Test
    void resolveELancaValorQuandoMatchConfiavel() {
        Vehicle veiculo = veiculoComTipo("ABC1234", "Fiat", "Strada", 2022, VehicleType.CARRO);
        when(vehicles.findAllByTipoIn(List.of(VehicleType.CARRO, VehicleType.MOTO))).thenReturn(List.of(veiculo));
        when(marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(veiculo.getId())).thenReturn(Optional.empty());

        when(fipeClient.marcas("carros")).thenReturn(List.of(new FipeMarca("21", "Fiat")));
        when(fipeClient.modelos("carros", "21"))
                .thenReturn(List.of(new FipeModeloResumo(100, "STRADA FREEDOM 1.3 FLEX 8V CD")));
        when(fipeClient.anos("carros", "21", "100")).thenReturn(List.of(new FipeAno("2022-1", "2022 Gasolina")));
        when(fipeClient.valor("carros", "21", "100", "2022-1"))
                .thenReturn(new FipeValor("R$ 75.000,00", "005340-1"));

        job.run();

        ArgumentCaptor<VehicleMarketValue> captor = ArgumentCaptor.forClass(VehicleMarketValue.class);
        verify(marketValues).save(captor.capture());
        VehicleMarketValue salvo = captor.getValue();
        assertEquals(veiculo.getId(), salvo.getVehicleId());
        assertEquals(new BigDecimal("75000.00"), salvo.getValorFipe());
        assertEquals("005340-1", salvo.getCodigoFipe());
        assertEquals(LocalDate.now(), salvo.getDataReferencia());
    }

    @Test
    void naoReconsultaVeiculoComValorRecente() {
        Vehicle veiculo = veiculoComTipo("ABC1234", "Fiat", "Strada", 2022, VehicleType.CARRO);
        when(vehicles.findAllByTipoIn(any())).thenReturn(List.of(veiculo));
        VehicleMarketValue recente =
                new VehicleMarketValue(veiculo.getId(), new BigDecimal("70000"), LocalDate.now().minusDays(5), null);
        when(marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(veiculo.getId()))
                .thenReturn(Optional.of(recente));

        job.run();

        verify(fipeClient, never()).marcas(any());
        verify(marketValues, never()).save(any());
    }

    @Test
    void naoLancaValorQuandoTipoNaoMapeiaParaFipe() {
        Vehicle veiculo = veiculoComTipo("VAN0001", "Renault", "Kangoo", 2021, VehicleType.VAN);
        when(vehicles.findAllByTipoIn(any())).thenReturn(List.of(veiculo));
        when(marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(veiculo.getId())).thenReturn(Optional.empty());

        job.run();

        verify(fipeClient, never()).marcas(any());
        verify(marketValues, never()).save(any());
    }

    @Test
    void naoLancaValorQuandoMarcaNaoResolve() {
        Vehicle veiculo = veiculoComTipo("XYZ9999", "MarcaInexistente", "ModeloX", 2022, VehicleType.CARRO);
        when(vehicles.findAllByTipoIn(any())).thenReturn(List.of(veiculo));
        when(marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(veiculo.getId())).thenReturn(Optional.empty());
        when(fipeClient.marcas("carros")).thenReturn(List.of(new FipeMarca("21", "Fiat")));

        job.run();

        verify(marketValues, never()).save(any());
    }

    @Test
    void umVeiculoComFalhaNaoDerrubaOsDemais() {
        Vehicle comErro = veiculoComTipo("ERR0001", "Fiat", "Strada", 2022, VehicleType.CARRO);
        Vehicle ok = veiculoComTipo("OK00001", "Fiat", "Strada", 2022, VehicleType.CARRO);
        when(vehicles.findAllByTipoIn(any())).thenReturn(List.of(comErro, ok));
        when(marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(any())).thenReturn(Optional.empty());
        when(fipeClient.marcas("carros"))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(List.of(new FipeMarca("21", "Fiat")));
        when(fipeClient.modelos("carros", "21"))
                .thenReturn(List.of(new FipeModeloResumo(100, "STRADA FREEDOM 1.3 FLEX 8V CD")));
        when(fipeClient.anos("carros", "21", "100")).thenReturn(List.of(new FipeAno("2022-1", "2022 Gasolina")));
        when(fipeClient.valor("carros", "21", "100", "2022-1"))
                .thenReturn(new FipeValor("R$ 75.000,00", "005340-1"));

        job.run();

        ArgumentCaptor<VehicleMarketValue> captor = ArgumentCaptor.forClass(VehicleMarketValue.class);
        verify(marketValues).save(captor.capture());
        assertEquals(ok.getId(), captor.getValue().getVehicleId());
    }
}
