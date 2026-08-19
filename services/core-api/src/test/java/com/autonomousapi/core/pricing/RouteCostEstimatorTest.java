package com.autonomousapi.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteCostEstimatorTest {

    private final FuelPriceReferenceRepository fuelPrices = mock(FuelPriceReferenceRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final RouteCostEstimator estimator = new RouteCostEstimator(fuelPrices, tenants);

    private final UUID tenantId = UUID.randomUUID();

    private Vehicle vehicleComAtributos(Map<String, Object> atributos) {
        Vehicle v = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 1000);
        v.update("ABC1234", "VW", "Saveiro", 2022, 1000, VehicleStatus.ATIVO, null, null, null, atributos);
        return v;
    }

    /** margemPadrao já nasce em 0.20 no construtor de Tenant — todos os testes usam esse padrão. */
    private Tenant novoTenant() {
        return new Tenant("Frota Teste");
    }

    @Test
    void calculaCustoEstimadoParaVeiculoACombustao() {
        Map<String, Object> atributos = new LinkedHashMap<>();
        atributos.put("combustivel", "diesel");
        atributos.put("consumoMedioKmPorLitro", 10.0);
        Vehicle vehicle = vehicleComAtributos(atributos);

        when(fuelPrices.findByTenantIdAndTipoCombustivel(tenantId, "diesel"))
                .thenReturn(Optional.of(new FuelPriceReference(tenantId, "diesel", new BigDecimal("6.00"), null)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(novoTenant()));

        Optional<RouteCostEstimator.Estimate> resultado = estimator.estimar(tenantId, vehicle, 100.0);

        assertTrue(resultado.isPresent());
        // 100km / 10km/l * R$6,00/l = R$60,00
        assertEquals(new BigDecimal("60.00"), resultado.get().custoEstimado());
        // 60 * 1.20 = 72.00 (margem padrão do Tenant, 0.20)
        assertEquals(new BigDecimal("72.00"), resultado.get().valorSugerido());
    }

    @Test
    void veiculoEletricoUsaKmPorKwhENuncaConsumoDeCombustao() {
        // Campo de combustão presente e absurdo (10000 km/l) — se o branch por tipo não
        // existisse, o resultado sairia comicamente barato em vez de usar kmPorKwh de verdade.
        Map<String, Object> atributos = new LinkedHashMap<>();
        atributos.put("combustivel", "eletrico");
        atributos.put("consumoMedioKmPorLitro", 10000.0);
        atributos.put("kmPorKwh", 5.0);
        Vehicle vehicle = vehicleComAtributos(atributos);

        when(fuelPrices.findByTenantIdAndTipoCombustivel(tenantId, "eletrico"))
                .thenReturn(Optional.of(new FuelPriceReference(tenantId, "eletrico", new BigDecimal("0.80"), null)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(novoTenant()));

        Optional<RouteCostEstimator.Estimate> resultado = estimator.estimar(tenantId, vehicle, 100.0);

        assertTrue(resultado.isPresent());
        // 100km / 5km/kWh * R$0,80/kWh = R$16,00 — usando kmPorKwh, não o campo de combustão.
        assertEquals(new BigDecimal("16.00"), resultado.get().custoEstimado());
    }

    @Test
    void semTipoCombustivelCadastradoDevolveVazio() {
        Vehicle vehicle = vehicleComAtributos(new LinkedHashMap<>());

        Optional<RouteCostEstimator.Estimate> resultado = estimator.estimar(tenantId, vehicle, 100.0);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void semConsumoCadastradoDevolveVazio() {
        Map<String, Object> atributos = new LinkedHashMap<>();
        atributos.put("combustivel", "flex");
        Vehicle vehicle = vehicleComAtributos(atributos);

        Optional<RouteCostEstimator.Estimate> resultado = estimator.estimar(tenantId, vehicle, 100.0);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void semPrecoDeReferenciaCadastradoParaOTenantDevolveVazio() {
        Map<String, Object> atributos = new LinkedHashMap<>();
        atributos.put("combustivel", "flex");
        atributos.put("consumoMedioKmPorLitro", 12.0);
        Vehicle vehicle = vehicleComAtributos(atributos);

        when(fuelPrices.findByTenantIdAndTipoCombustivel(tenantId, "flex")).thenReturn(Optional.empty());

        Optional<RouteCostEstimator.Estimate> resultado = estimator.estimar(tenantId, vehicle, 100.0);

        assertTrue(resultado.isEmpty());
    }
}
