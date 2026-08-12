package com.autonomousapi.core.vehicle.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.cost.dto.FleetCostEntryResponse;
import com.autonomousapi.core.vehicle.cost.dto.MonthlyCostResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Executa contra Postgres real as queries que mock não consegue validar: JPQL com join,
 * constructor expression e filtro por data. Cobre também isolamento por tenant no nível
 * de SQL — se a cláusula de tenant sumir de uma query, aqui quebra.
 */
class VehicleCostQueriesIntegrationTest extends IntegrationTestBase {

    @Autowired TenantRepository tenants;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleCostEntryRepository costs;
    @Autowired VehicleCostService service;

    private JwtPrincipal donoDaFrota;
    private JwtPrincipal outroTenant;

    @BeforeEach
    void prepararDoisTenants() {
        costs.deleteAll();
        vehicles.deleteAll();

        Tenant rotaCerta = tenants.save(new Tenant("RotaCerta " + UUID.randomUUID()));
        Tenant concorrente = tenants.save(new Tenant("Concorrente " + UUID.randomUUID()));
        donoDaFrota = new JwtPrincipal(UUID.randomUUID(), rotaCerta.getId(), "GESTOR_FROTA");
        outroTenant = new JwtPrincipal(UUID.randomUUID(), concorrente.getId(), "GESTOR_FROTA");

        Vehicle nosso = vehicles.save(new Vehicle(rotaCerta.getId(), "RTC1A23", "Fiat", "Fiorino", 2022, 32000));
        Vehicle alheio = vehicles.save(new Vehicle(concorrente.getId(), "XXX9Z99", "Ford", "Transit", 2021, 50000));

        costs.save(new VehicleCostEntry(nosso.getId(), VehicleCostCategory.COMBUSTIVEL,
                new BigDecimal("200.00"), "Abastecimento", LocalDate.now().minusDays(10)));
        costs.save(new VehicleCostEntry(nosso.getId(), VehicleCostCategory.MANUTENCAO,
                new BigDecimal("350.00"), "Troca de óleo; e filtro", LocalDate.now().minusDays(5)));
        costs.save(new VehicleCostEntry(alheio.getId(), VehicleCostCategory.MANUTENCAO,
                new BigDecimal("999.00"), "Custo do concorrente", LocalDate.now().minusDays(5)));
    }

    @Test
    void exportCsvNaoEstouraORangeDeDataDoPostgres() {
        // Regressão direta: a primeira versão passava LocalDate.MIN como parâmetro e o
        // Postgres respondia "date out of range" — invisível para teste com mock.
        String csv = service.exportCsv(donoDaFrota);

        String[] linhas = csv.strip().split("\n");
        assertEquals("Placa;Marca;Modelo;Categoria;Descricao;Data;Valor", linhas[0]);
        assertEquals(3, linhas.length, "cabeçalho + 2 lançamentos do tenant");
        // Descrição com ';' precisa sair entre aspas para não quebrar a coluna.
        assertTrue(csv.contains("\"Troca de óleo; e filtro\""));
    }

    @Test
    void exportCsvNaoVazaCustoDeOutroTenant() {
        String csv = service.exportCsv(donoDaFrota);

        assertFalse(csv.contains("Custo do concorrente"));
        assertFalse(csv.contains("XXX9Z99"));
    }

    @Test
    void fleetCostsResolveConstructorExpressionEJoinComVeiculo() {
        // A constructor expression referencia a classe pelo nome completo em string:
        // um erro de digitação compila normalmente e só falha em runtime. Só um teste
        // que executa a query de verdade pega isso.
        List<FleetCostEntryResponse> todos = service.fleetCosts(donoDaFrota, null);

        assertEquals(2, todos.size());
        assertTrue(todos.stream().allMatch(c -> "RTC1A23".equals(c.plate())), "placa vem do join");
        assertTrue(todos.stream().allMatch(c -> "Fiat".equals(c.brand())));
    }

    @Test
    void fleetCostsFiltraPorCategoria() {
        List<FleetCostEntryResponse> manutencao =
                service.fleetCosts(donoDaFrota, VehicleCostCategory.MANUTENCAO);

        assertEquals(1, manutencao.size());
        assertEquals(new BigDecimal("350.00"), manutencao.get(0).amount());
    }

    @Test
    void fleetCostsNaoVazaEntreTenants() {
        assertTrue(service.fleetCosts(outroTenant, VehicleCostCategory.COMBUSTIVEL).isEmpty());
        assertEquals(1, service.fleetCosts(outroTenant, VehicleCostCategory.MANUTENCAO).size());
    }

    @Test
    void tendenciaMensalSomaNoBancoEPreencheMesesVazios() {
        List<MonthlyCostResponse> tendencia = service.monthlyTrend(donoDaFrota);

        assertEquals(6, tendencia.size(), "sempre 6 meses, mesmo sem lançamento");
        BigDecimal somaDaJanela = tendencia.stream()
                .map(MonthlyCostResponse::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Os dois lançamentos do tenant caem dentro da janela de 6 meses.
        assertEquals(0, new BigDecimal("550.00").compareTo(somaDaJanela));
    }
}
