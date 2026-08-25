package com.autonomousapi.core.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanInvalidException;
import com.autonomousapi.core.expense.dto.ExpenseEntryRequest;
import com.autonomousapi.core.expense.dto.ExpenseSummaryResponse;
import com.autonomousapi.core.expense.dto.MonthlyCostResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseEntryServiceTest {

    private final VehicleRepository vehicleRepo = mock(VehicleRepository.class);
    private final ExpenseEntryRepository expenseRepo = mock(ExpenseEntryRepository.class);
    private final ExpenseEntryService service = new ExpenseEntryService(vehicleRepo, expenseRepo);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal =
            new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void calculaCustoPorKmSobreKmRodadoDesdeOCadastro() {
        UUID vehicleId = UUID.randomUUID();
        // Cadastrado com 500 km (odometroInicial) e depois atualizado pra 1000 km: só os
        // 500 km rodados desde o cadastro entram na conta, não o odômetro total.
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 500);
        vehicle.update("ABC1234", "VW", "Saveiro", 2022, 1000, vehicle.getStatus(), null, null, null, null);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(expenseRepo.sumValorByVehicleId(vehicle.getId())).thenReturn(new BigDecimal("500.00"));

        ExpenseSummaryResponse summary = service.summary(principal, vehicleId);

        assertEquals(new BigDecimal("500.00"), summary.totalValor());
        assertEquals(1000, summary.odometerKm());
        assertEquals(500, summary.kmRodado());
        assertEquals(new BigDecimal("1.00"), summary.custoPorKm());
    }

    @Test
    void custoPorKmNuloQuandoAindaNaoRodouKmDesdeOCadastro() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ZER0000", "VW", "Novo", 2024, 0);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(expenseRepo.sumValorByVehicleId(vehicle.getId())).thenReturn(BigDecimal.ZERO);

        ExpenseSummaryResponse summary = service.summary(principal, vehicleId);

        assertEquals(0, summary.kmRodado());
        assertNull(summary.custoPorKm());
    }

    @Test
    void tendenciaMensalAgregaCustosDeTodaFrotaEPreencheMesesSemCusto() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth mesAnterior = currentMonth.minusMonths(1);
        UUID veiculoA = UUID.randomUUID();
        UUID veiculoB = UUID.randomUUID();

        when(expenseRepo.findAllByTenantIdAndDataGreaterThanEqualOrderByData(eq(tenantId), any())).thenReturn(List.of(
                new ExpenseEntry(tenantId, veiculoA, ExpenseCategory.COMBUSTIVEL,
                        new BigDecimal("100.00"), "Posto", mesAnterior.atDay(5), null, null),
                new ExpenseEntry(tenantId, veiculoB, ExpenseCategory.MANUTENCAO,
                        new BigDecimal("50.00"), "Troca de óleo", mesAnterior.atDay(10), null, null),
                new ExpenseEntry(tenantId, veiculoA, ExpenseCategory.OUTRO,
                        new BigDecimal("30.00"), "Pedágio", currentMonth.atDay(1), null, null)));

        List<MonthlyCostResponse> trend = service.monthlyTrend(principal);

        assertEquals(6, trend.size());
        MonthlyCostResponse mesAnteriorResp = trend.stream()
                .filter(m -> m.month().equals(mesAnterior.toString())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("150.00"), mesAnteriorResp.total());
        MonthlyCostResponse mesAtualResp = trend.stream()
                .filter(m -> m.month().equals(currentMonth.toString())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("30.00"), mesAtualResp.total());
        MonthlyCostResponse mesSemCusto = trend.stream()
                .filter(m -> m.month().equals(currentMonth.minusMonths(3).toString())).findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO, mesSemCusto.total());
    }

    @Test
    void exportaCsvComCabecalhoEDadosDoVeiculo() {
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 1000);
        when(vehicleRepo.findAllById(List.of(vehicle.getId()))).thenReturn(List.of(vehicle));
        when(expenseRepo.findAllByTenantIdAndDataGreaterThanEqualOrderByData(eq(tenantId), any())).thenReturn(List.of(
                new ExpenseEntry(tenantId, vehicle.getId(), ExpenseCategory.COMBUSTIVEL,
                        new BigDecimal("150.50"), "Abastecimento; posto X", LocalDate.of(2026, 1, 10), null, null)));

        String csv = service.exportCsv(principal);

        String[] lines = csv.split("\n");
        assertEquals("Placa;Marca;Modelo;Categoria;Descricao;Data;Valor", lines[0]);
        assertEquals("ABC1234;VW;Saveiro;COMBUSTIVEL;\"Abastecimento; posto X\";2026-01-10;150.50", lines[1]);
    }

    @Test
    void naoAdicionaDespesaEmVeiculoDeOutroTenant() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.empty());
        ExpenseEntryRequest req = new ExpenseEntryRequest(
                null, ExpenseCategory.COMBUSTIVEL, new BigDecimal("100.00"), "Abastecimento", LocalDate.now(), null, null);

        assertThrows(NotFoundException.class, () -> service.create(principal, vehicleId, req));
    }

    @Test
    void aceitaDespesaDeFrotaSemVeiculo() {
        ExpenseEntryRequest req = new ExpenseEntryRequest(
                null, ExpenseCategory.SEGURO, new BigDecimal("1200.00"), "Seguro corporativo", LocalDate.now(), null, null);

        var resp = service.create(principal, null, req);

        assertNull(resp.vehicleId());
        assertEquals("SEGURO", resp.categoria());
    }

    @Test
    void rejeitaLitrosOuOdometroForaDeCombustivel() {
        ExpenseEntryRequest req = new ExpenseEntryRequest(
                null, ExpenseCategory.MANUTENCAO, new BigDecimal("300.00"), "Revisão", LocalDate.now(),
                new BigDecimal("10.0"), null);

        assertThrows(RoutePlanInvalidException.class, () -> service.create(principal, null, req));
    }
}
