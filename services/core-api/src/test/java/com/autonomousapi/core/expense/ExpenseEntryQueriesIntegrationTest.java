package com.autonomousapi.core.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.expense.dto.FleetExpenseEntryResponse;
import com.autonomousapi.core.expense.dto.MonthlyCostResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Executa contra Postgres real as queries que mock não consegue validar: JPQL com left join,
 * constructor expression e filtro por data. Cobre também isolamento por tenant no nível
 * de SQL — se a cláusula de tenant sumir de uma query, aqui quebra.
 */
class ExpenseEntryQueriesIntegrationTest extends IntegrationTestBase {

    @Autowired TenantRepository tenants;
    @Autowired VehicleRepository vehicles;
    @Autowired ExpenseEntryRepository expenses;
    @Autowired ExpenseEntryService service;
    @Autowired DataSource dataSource;

    private JwtPrincipal donoDaFrota;
    private JwtPrincipal outroTenant;
    private UUID rotaCertaId;

    @BeforeEach
    void prepararDoisTenants() {
        expenses.deleteAll();
        vehicles.deleteAll();

        Tenant rotaCerta = tenants.save(new Tenant("RotaCerta " + UUID.randomUUID()));
        Tenant concorrente = tenants.save(new Tenant("Concorrente " + UUID.randomUUID()));
        rotaCertaId = rotaCerta.getId();
        donoDaFrota = new JwtPrincipal(UUID.randomUUID(), rotaCerta.getId(), "GESTOR_FROTA");
        outroTenant = new JwtPrincipal(UUID.randomUUID(), concorrente.getId(), "GESTOR_FROTA");

        Vehicle nosso = vehicles.save(new Vehicle(rotaCerta.getId(), "RTC1A23", "Fiat", "Fiorino", 2022, 32000));
        Vehicle alheio = vehicles.save(new Vehicle(concorrente.getId(), "XXX9Z99", "Ford", "Transit", 2021, 50000));

        expenses.save(new ExpenseEntry(rotaCerta.getId(), nosso.getId(), ExpenseCategory.COMBUSTIVEL,
                new BigDecimal("200.00"), "Abastecimento", LocalDate.now().minusDays(10), null, null));
        expenses.save(new ExpenseEntry(rotaCerta.getId(), nosso.getId(), ExpenseCategory.MANUTENCAO,
                new BigDecimal("350.00"), "Troca de óleo; e filtro", LocalDate.now().minusDays(5), null, null));
        expenses.save(new ExpenseEntry(concorrente.getId(), alheio.getId(), ExpenseCategory.MANUTENCAO,
                new BigDecimal("999.00"), "Custo do concorrente", LocalDate.now().minusDays(5), null, null));
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
    void fleetExpensesResolveConstructorExpressionEJoinComVeiculo() {
        // A constructor expression referencia a classe pelo nome completo em string:
        // um erro de digitação compila normalmente e só falha em runtime. Só um teste
        // que executa a query de verdade pega isso.
        List<FleetExpenseEntryResponse> todos = service.fleetExpenses(donoDaFrota, null);

        assertEquals(2, todos.size());
        assertTrue(todos.stream().allMatch(c -> "RTC1A23".equals(c.plate())), "placa vem do join");
        assertTrue(todos.stream().allMatch(c -> "Fiat".equals(c.brand())));
    }

    @Test
    void fleetExpensesFiltraPorCategoria() {
        List<FleetExpenseEntryResponse> manutencao =
                service.fleetExpenses(donoDaFrota, ExpenseCategory.MANUTENCAO);

        assertEquals(1, manutencao.size());
        assertEquals(new BigDecimal("350.00"), manutencao.get(0).valor());
    }

    @Test
    void fleetExpensesNaoVazaEntreTenants() {
        assertTrue(service.fleetExpenses(outroTenant, ExpenseCategory.COMBUSTIVEL).isEmpty());
        assertEquals(1, service.fleetExpenses(outroTenant, ExpenseCategory.MANUTENCAO).size());
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

    @Test
    void despesaDeFrotaSemVeiculoApareceComVehicleIdNulo() {
        expenses.save(new ExpenseEntry(rotaCertaId, null, ExpenseCategory.SEGURO,
                new BigDecimal("800.00"), "Seguro corporativo", LocalDate.now().minusDays(1), null, null));

        List<FleetExpenseEntryResponse> todos = service.fleetExpenses(donoDaFrota, ExpenseCategory.SEGURO);

        assertEquals(1, todos.size());
        assertEquals(null, todos.get(0).vehicleId());
        assertEquals(null, todos.get(0).plate());
    }

    @Test
    void bancoRejeitaCategoriaForaDaListaPermitida() {
        // Trava de banco (migration V23) — não só validação em Java, mesma disciplina já
        // usada nas validações de data em route_plan.
        assertThrows(SQLException.class, () -> executarInsert(
                "insert into expense_entry (id, tenant_id, categoria, valor, data, fonte, created_at) "
                        + "values (gen_random_uuid(), '" + rotaCertaId + "', 'categoria_inventada', 10.0, "
                        + "current_date, 'manual', now())"));
    }

    @Test
    void bancoRejeitaLitrosPreenchidoForaDeCategoriaCombustivel() {
        assertThrows(SQLException.class, () -> executarInsert(
                "insert into expense_entry (id, tenant_id, categoria, valor, data, fonte, litros_ou_kwh, created_at) "
                        + "values (gen_random_uuid(), '" + rotaCertaId + "', 'manutencao', 10.0, "
                        + "current_date, 'manual', 5.0, now())"));
    }

    private void executarInsert(String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    void somaPorCategoriaAgregaCorretamenteNoIntervalo() {
        List<com.autonomousapi.core.expense.dto.CategoryTotal> totais =
                service.summaryByCategory(donoDaFrota, LocalDate.now().minusDays(30), LocalDate.now());

        assertTrue(totais.stream().anyMatch(t ->
                t.categoria() == ExpenseCategory.COMBUSTIVEL && new BigDecimal("200.00").compareTo(t.total()) == 0));
        assertTrue(totais.stream().anyMatch(t ->
                t.categoria() == ExpenseCategory.MANUTENCAO && new BigDecimal("350.00").compareTo(t.total()) == 0));
    }
}
