package com.autonomousapi.core.expense;

import com.autonomousapi.core.expense.dto.CategoryTotal;
import com.autonomousapi.core.expense.dto.FleetExpenseEntryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntryRepository extends JpaRepository<ExpenseEntry, UUID> {

    List<ExpenseEntry> findAllByVehicleIdOrderByDataDesc(UUID vehicleId);

    Optional<ExpenseEntry> findByIdAndVehicleId(UUID id, UUID vehicleId);

    @Query("select coalesce(sum(e.valor), 0) from ExpenseEntry e where e.vehicleId = :vehicleId")
    BigDecimal sumValorByVehicleId(@Param("vehicleId") UUID vehicleId);

    /** Despesas do tenant desde {@code since} — usado no gráfico de tendência do dashboard e no CSV. */
    List<ExpenseEntry> findAllByTenantIdAndDataGreaterThanEqualOrderByData(UUID tenantId, LocalDate since);

    /**
     * Despesas da frota inteira já com os dados do veículo (quando houver — despesa de frota
     * não tem veículo), em UMA query. left join de propósito: vehicleId é nullable.
     * {@code categoria} nulo = sem filtro (mesmo padrão de {@link #sumForBudgetScope}).
     *
     * Paginada — countQuery explícita porque o Spring Data não deriva bem a contagem de uma
     * constructor-expression com left join. Mantém {@code order by e.data desc} fixo em vez de
     * expor Sort dinâmico via Pageable: evita ORDER BY duplicado (o da JPQL + o do Pageable).
     */
    @Query(
            value = "select new com.autonomousapi.core.expense.dto.FleetExpenseEntryResponse("
                    + "e.id, v.id, v.plate, v.brand, v.model, e.categoria, e.valor, e.descricao, e.data) "
                    + "from ExpenseEntry e left join com.autonomousapi.core.vehicle.Vehicle v on v.id = e.vehicleId "
                    + "where e.tenantId = :tenantId and (:categoria is null or e.categoria = :categoria) "
                    + "order by e.data desc",
            countQuery = "select count(e) from ExpenseEntry e "
                    + "where e.tenantId = :tenantId and (:categoria is null or e.categoria = :categoria)")
    Page<FleetExpenseEntryResponse> findFleetExpenses(
            @Param("tenantId") UUID tenantId,
            @Param("categoria") ExpenseCategory categoria,
            Pageable pageable);

    /** Soma por categoria no intervalo — alimenta a "Visão geral" da aba Custos. */
    @Query("select new com.autonomousapi.core.expense.dto.CategoryTotal(e.categoria, sum(e.valor)) "
            + "from ExpenseEntry e "
            + "where e.tenantId = :tenantId and e.data >= :from and e.data <= :to "
            + "group by e.categoria")
    List<CategoryTotal> sumByCategoria(
            @Param("tenantId") UUID tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Soma no escopo de um {@code budget} — usada por {@code BudgetService#progressoAtual}.
     * {@code vehicleId}/{@code categoria} nulos = sem filtro (orçamento de frota / de todas
     * as categorias); quando o orçamento é de frota inteira, despesas sem veículo (frota,
     * ex. seguro corporativo) também entram na soma, porque {@code e.vehicleId = null} já
     * cai no "sem filtro".
     */
    @Query("select coalesce(sum(e.valor), 0) from ExpenseEntry e where e.tenantId = :tenantId "
            + "and (:vehicleId is null or e.vehicleId = :vehicleId) "
            + "and (:categoria is null or e.categoria = :categoria) "
            + "and e.data >= :from and e.data <= :to")
    BigDecimal sumForBudgetScope(
            @Param("tenantId") UUID tenantId,
            @Param("vehicleId") UUID vehicleId,
            @Param("categoria") ExpenseCategory categoria,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
