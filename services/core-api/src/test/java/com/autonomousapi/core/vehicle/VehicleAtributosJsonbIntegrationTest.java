package com.autonomousapi.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.expense.ExpenseEntryRepository;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atributos em jsonb (ADR 0008) contra Postgres real.
 *
 * Precisa ser teste de integração: com repositório mockado nada é serializado, então o
 * mapeamento @JdbcTypeCode(SqlTypes.JSON), o tipo de cada valor na volta e a consulta por
 * contenção passariam verdes sem nunca terem sido exercitados.
 */
@Transactional
class VehicleAtributosJsonbIntegrationTest extends IntegrationTestBase {

    @Autowired VehicleRepository vehicles;
    @Autowired TenantRepository tenants;
    @Autowired ExpenseEntryRepository custos;
    @Autowired EntityManager em;

    private UUID tenantId;

    @BeforeEach
    void limparEPreparar() {
        // Custo antes de veículo: o banco de teste é compartilhado entre as classes de
        // integração, e expense_entry tem FK para vehicle.
        custos.deleteAll();
        vehicles.deleteAll();
        tenantId = tenants.save(new Tenant("Frota jsonb " + UUID.randomUUID())).getId();
    }

    private Vehicle salvar(String placa, Map<String, Object> atributos) {
        Vehicle v = new Vehicle(tenantId, placa, "Fiat", "Fiorino", 2022, 1000);
        v.update(placa, "Fiat", "Fiorino", 2022, 1000, VehicleStatus.ATIVO, null, null, null, atributos);
        return vehicles.save(v);
    }

    @Test
    void atributosSobrevivemAIdaEVoltaDoBanco() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("categoria", "motocicleta");
        attrs.put("cilindradas", 162);
        attrs.put("temBau", true);

        UUID id = salvar("JSN0001", attrs).getId();
        em.flush();
        em.clear(); // sem isso o teste leria o objeto do cache de primeiro nível, não do banco

        Map<String, Object> lidos = vehicles.findById(id).orElseThrow().getAtributos();

        assertEquals("motocicleta", lidos.get("categoria"));
        assertEquals(162, ((Number) lidos.get("cilindradas")).intValue());
        assertEquals(Boolean.TRUE, lidos.get("temBau"));
    }

    @Test
    void veiculosDeTiposDiferentesConvivemComChavesDiferentes() {
        // É o ponto do ADR 0008: heterogeneidade de schema sem coluna nula para todo mundo.
        salvar("JSN0002", Map.of("categoria", "eletrico", "autonomiaKm", 320, "conector", "Tipo 2"));
        salvar("JSN0003", Map.of("categoria", "utilitario", "capacidadeCargaKg", 650));

        List<Vehicle> todos = vehicles.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        assertEquals(2, todos.size());
        assertTrue(todos.stream().anyMatch(v -> v.getAtributos().containsKey("autonomiaKm")));
        assertTrue(todos.stream().anyMatch(v -> v.getAtributos().containsKey("capacidadeCargaKg")));
    }

    @Test
    void consultaPorContencaoUsaOJsonbEIsolaPorTenant() {
        salvar("JSN0004", Map.of("categoria", "eletrico", "autonomiaKm", 320));
        salvar("JSN0005", Map.of("categoria", "utilitario"));

        UUID outroTenant = tenants.save(new Tenant("Concorrente " + UUID.randomUUID())).getId();
        Vehicle alheio = new Vehicle(outroTenant, "XXX9Z99", "Ford", "Transit", 2021, 10);
        alheio.update("XXX9Z99", "Ford", "Transit", 2021, 10, VehicleStatus.ATIVO, null, null, null,
                Map.of("categoria", "eletrico"));
        vehicles.save(alheio);
        em.flush();

        // O operador @> é o que o índice GIN jsonb_path_ops atende (migration V9).
        @SuppressWarnings("unchecked")
        List<String> placas = em.createNativeQuery(
                        // core. explícito: default_schema do Hibernate não se aplica a query nativa.
                        "select plate from core.vehicle where tenant_id = :t and atributos @> '{\"categoria\":\"eletrico\"}'::jsonb")
                .setParameter("t", tenantId)
                .getResultList();

        assertEquals(List.of("JSN0004"), placas, "não pode trazer o elétrico do outro tenant");
    }

    @Test
    void veiculoSemAtributosGravaObjetoVazioENaoNulo() {
        // A coluna é NOT NULL com default '{}': passar null não pode quebrar a inserção.
        UUID id = salvar("JSN0006", null).getId();
        em.flush();
        em.clear();

        assertTrue(vehicles.findById(id).orElseThrow().getAtributos().isEmpty());
    }
}
