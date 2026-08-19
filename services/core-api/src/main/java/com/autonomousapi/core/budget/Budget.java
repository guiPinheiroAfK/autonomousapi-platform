package com.autonomousapi.core.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import com.autonomousapi.core.expense.ExpenseCategory;

/**
 * Orçamento com alerta de estouro (spec 10, item 2). Escopo é veículo específico OU frota
 * inteira (vehicleId nullable); categoria também é opcional (orçamento geral vs. só de uma
 * categoria, ex. "R$2.000/mês só de manutenção").
 *
 * {@code ultimoPatamarNotificado}/{@code periodoReferencia} existem só pro dedup do
 * {@code BudgetAlertJob} — orçamento estourado é estado persistente (dura o mês inteiro),
 * então sem esse controle o alerta repetiria todo dia até o mês virar.
 */
@Entity
@Table(name = "budget")
public class Budget {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 20, updatable = false)
    private ExpenseCategory categoria;

    @Column(name = "periodo", nullable = false, length = 10, updatable = false)
    private String periodo;

    @Column(name = "valor_limite", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "ultimo_patamar_notificado", length = 3)
    private String ultimoPatamarNotificado;

    @Column(name = "periodo_referencia", length = 7)
    private String periodoReferencia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Budget() {
        // JPA
    }

    public Budget(UUID tenantId, UUID vehicleId, ExpenseCategory categoria, BigDecimal valorLimite) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.vehicleId = vehicleId;
        this.categoria = categoria;
        this.periodo = "MENSAL";
        this.valorLimite = valorLimite;
        this.createdAt = Instant.now();
    }

    /**
     * Se o mês corrente é diferente do período de referência salvo, reseta o patamar
     * notificado — orçamento "estoura de novo" a cada mês, o alerta pode disparar de novo.
     */
    public void garantirPeriodoAtual() {
        String mesAtual = YearMonth.now().toString();
        if (!mesAtual.equals(periodoReferencia)) {
            this.periodoReferencia = mesAtual;
            this.ultimoPatamarNotificado = null;
        }
    }

    /**
     * @return true se {@code novoPatamar} é uma transição real (maior que o já notificado) —
     * só nesse caso o job deve notificar; grava o novo patamar como efeito colateral.
     */
    public boolean avancarPatamarSeNovo(String novoPatamar) {
        if (nivel(novoPatamar) <= nivel(ultimoPatamarNotificado)) {
            return false;
        }
        this.ultimoPatamarNotificado = novoPatamar;
        return true;
    }

    private static int nivel(String patamar) {
        if (patamar == null) return 0;
        return switch (patamar) {
            case "80" -> 1;
            case "100" -> 2;
            default -> 0;
        };
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public ExpenseCategory getCategoria() {
        return categoria;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getValorLimite() {
        return valorLimite;
    }

    public String getUltimoPatamarNotificado() {
        return ultimoPatamarNotificado;
    }

    public String getPeriodoReferencia() {
        return periodoReferencia;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
