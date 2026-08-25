package com.autonomousapi.core.expense;

import com.autonomousapi.core.config.CacheConfig;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.error.RoutePlanInvalidException;
import com.autonomousapi.core.expense.dto.CategoryTotal;
import com.autonomousapi.core.expense.dto.ExpenseEntryRequest;
import com.autonomousapi.core.expense.dto.ExpenseEntryResponse;
import com.autonomousapi.core.expense.dto.ExpenseSummaryResponse;
import com.autonomousapi.core.expense.dto.FleetExpenseEntryResponse;
import com.autonomousapi.core.expense.dto.MonthlyCostResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Despesas categorizadas (spec 10), agora sobre {@link ExpenseEntry} em vez de
 * {@code vehicle_cost_entry} (migration V23). Custo por km é {@code totalValor / kmRodado
 * desde o cadastro} (migration V24), não mais {@code totalValor / odometerKm} total do
 * veículo — ver {@link ExpenseSummaryResponse}.
 */
@Service
public class ExpenseEntryService {

    /** Gráfico de tendência do dashboard mostra os últimos 6 meses (mês corrente incluso). */
    private static final int TREND_MONTHS = 6;

    private final VehicleRepository vehicles;
    private final ExpenseEntryRepository expenses;

    public ExpenseEntryService(VehicleRepository vehicles, ExpenseEntryRepository expenses) {
        this.vehicles = vehicles;
        this.expenses = expenses;
    }

    /**
     * {@code vehicleIdFromPath} vence o {@code vehicleId} do corpo quando informado (endpoint
     * escopado por veículo) — nunca confia no corpo pra decidir o veículo, mesmo padrão já
     * usado pra {@code collectionPointId} em RoutePlanService. Quando ambos são nulos, a
     * despesa é de frota (sem veículo).
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_COST_TREND, key = "#principal.tenantId()")
    @Transactional
    public ExpenseEntryResponse create(JwtPrincipal principal, UUID vehicleIdFromPath, ExpenseEntryRequest req) {
        UUID vehicleId = vehicleIdFromPath != null ? vehicleIdFromPath : req.vehicleId();
        if (vehicleId != null) {
            findOwnedVehicle(principal, vehicleId);
        }
        if (req.categoria() != ExpenseCategory.COMBUSTIVEL
                && (req.litrosOuKwh() != null || req.odometro() != null)) {
            throw new RoutePlanInvalidException(
                    "litrosOuKwh/odometro só são válidos para categoria COMBUSTIVEL.");
        }
        ExpenseEntry entry = new ExpenseEntry(
                principal.tenantId(), vehicleId, req.categoria(), req.valor(),
                req.descricao(), req.data(), req.litrosOuKwh(), req.odometro());
        expenses.save(entry);
        return ExpenseEntryResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<ExpenseEntryResponse> list(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        return expenses.findAllByVehicleIdOrderByDataDesc(vehicle.getId()).stream()
                .map(ExpenseEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        BigDecimal total = expenses.sumValorByVehicleId(vehicle.getId());
        int odometerKm = vehicle.getOdometerKm();
        int kmRodado = odometerKm - vehicle.getOdometroInicial();
        BigDecimal custoPorKm = kmRodado <= 0
                ? null
                : total.divide(BigDecimal.valueOf(kmRodado), 2, RoundingMode.HALF_UP);
        return new ExpenseSummaryResponse(vehicle.getId(), total, odometerKm, kmRodado, custoPorKm);
    }

    /**
     * Soma de despesas por mês em toda a frota do tenant, últimos {@value #TREND_MONTHS} meses.
     *
     * Cacheado por 60s. A chave é o tenantId — nunca o principal inteiro: dois usuários do
     * mesmo tenant devem compartilhar a entrada, e usuários de tenants diferentes JAMAIS
     * podem colidir. Trocar isso por uma chave sem tenant vaza dado entre clientes.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_COST_TREND, key = "#principal.tenantId()")
    @Transactional(readOnly = true)
    public List<MonthlyCostResponse> monthlyTrend(JwtPrincipal principal) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(TREND_MONTHS - 1L);
        LocalDate since = firstMonth.atDay(1);

        Map<YearMonth, BigDecimal> totalsByMonth = expenses
                .findAllByTenantIdAndDataGreaterThanEqualOrderByData(principal.tenantId(), since)
                .stream()
                .collect(Collectors.groupingBy(
                        e -> YearMonth.from(e.getData()),
                        Collectors.reducing(BigDecimal.ZERO, ExpenseEntry::getValor, BigDecimal::add)));

        List<MonthlyCostResponse> trend = new ArrayList<>();
        for (int i = 0; i < TREND_MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            trend.add(new MonthlyCostResponse(month.toString(), totalsByMonth.getOrDefault(month, BigDecimal.ZERO)));
        }
        return trend;
    }

    /**
     * Despesas da frota inteira, opcionalmente filtradas por categoria, já com os dados do
     * veículo. Resolve em uma query o que antes eram 1+N requisições vindas do front.
     */
    @Transactional(readOnly = true)
    public Page<FleetExpenseEntryResponse> fleetExpenses(
            JwtPrincipal principal, ExpenseCategory categoria, Pageable pageable) {
        return expenses.findFleetExpenses(principal.tenantId(), categoria, pageable);
    }

    /** Soma por categoria num intervalo — alimenta a "Visão geral" da aba Custos. */
    @Transactional(readOnly = true)
    public List<CategoryTotal> summaryByCategory(JwtPrincipal principal, LocalDate from, LocalDate to) {
        return expenses.sumByCategoria(principal.tenantId(), from, to);
    }

    /** Relatório de despesas de toda a frota do tenant, desde o início, em CSV (spec 05, Fase 1: web). */
    @Transactional(readOnly = true)
    public String exportCsv(JwtPrincipal principal) {
        // LocalDate.MIN estoura o range de "date" do Postgres (4713 BC..5874897 AD) — usa
        // uma data bem anterior a qualquer lançamento real em vez do mínimo teórico do Java.
        List<ExpenseEntry> entries = expenses
                .findAllByTenantIdAndDataGreaterThanEqualOrderByData(principal.tenantId(), LocalDate.of(2000, 1, 1));

        // Só os veículos com despesa lançada, não a frota inteira do tenant (achado da
        // auditoria de cleanup).
        List<UUID> vehicleIds = entries.stream().map(ExpenseEntry::getVehicleId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Vehicle> vehicleById = vehicles.findAllById(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v));

        StringBuilder csv = new StringBuilder("Placa;Marca;Modelo;Categoria;Descricao;Data;Valor\n");
        for (ExpenseEntry e : entries) {
            Vehicle v = e.getVehicleId() != null ? vehicleById.get(e.getVehicleId()) : null;
            csv.append(csvField(v != null ? v.getPlate() : "")).append(';')
                    .append(csvField(v != null ? v.getBrand() : "")).append(';')
                    .append(csvField(v != null ? v.getModel() : "")).append(';')
                    .append(csvField(e.getCategoria().name())).append(';')
                    .append(csvField(e.getDescricao())).append(';')
                    .append(e.getData()).append(';')
                    .append(e.getValor())
                    .append('\n');
        }
        return csv.toString();
    }

    private static String csvField(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(";") || escaped.contains("\"") || escaped.contains("\n")
                ? "\"" + escaped + "\""
                : escaped;
    }

    /** Deleção não precisa mais resolver o veículo pra escopar por tenant — tenant_id agora
     *  é nativo em expense_entry (migration V23), diferente de vehicle_cost_entry. */
    @CacheEvict(cacheNames = CacheConfig.CACHE_COST_TREND, key = "#principal.tenantId()")
    @Transactional
    public void delete(JwtPrincipal principal, UUID expenseId) {
        ExpenseEntry entry = Lookups.orNotFound(
                expenses.findById(expenseId).filter(e -> e.getTenantId().equals(principal.tenantId())),
                "Despesa não encontrada.");
        expenses.delete(entry);
    }

    private Vehicle findOwnedVehicle(JwtPrincipal principal, UUID vehicleId) {
        return Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, principal.tenantId()), "Veículo não encontrado.");
    }
}
