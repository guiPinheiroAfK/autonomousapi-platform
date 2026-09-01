package com.autonomousapi.core.routeplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Rota com múltiplas paradas de coleta/entrega (spec 02, "Roteamento com múltiplos
 * pontos"). {@code status} nunca é setado de fora de {@link RoutePlanService#completeStop}
 * — é sempre derivado do estado das {@link RouteStop} (ver migration V20).
 */
@Entity
@Table(name = "route_plan")
public class RoutePlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "gestor_user_id", nullable = false, updatable = false)
    private UUID gestorUserId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutePlanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 10, updatable = false)
    private RouteCategoria categoria;

    /** {@code updatable=false} removido (spec 11, gap "edição de rota já atribuída") — só
     *  {@link RoutePlanService#update} muda este campo, e só quando a rota ainda está
     *  {@code PLANEJADA}. */
    @Column(name = "data_execucao", nullable = false)
    private LocalDate dataExecucao;

    @Column(name = "valor")
    private BigDecimal valor;

    /** Só preenchido para TRANSFER com veículo informado e consumo/preço de referência
     *  cadastrados (spec 09) — ver {@link com.autonomousapi.core.pricing.RouteCostEstimator}.
     *  Calculado uma vez na criação, nunca recalculado depois: é o valor usado como base de
     *  {@code margemRealizada} quando a rota concluir. */
    @Column(name = "custo_estimado")
    private BigDecimal custoEstimado;

    @Column(name = "pricing_formula_version", length = 10)
    private String pricingFormulaVersion;

    /** Chave de agrupamento entre pernas de uma viagem de ida e volta (spec 13) — não é
     *  FK, não referencia outro {@code route_plan}: duas linhas com o mesmo valor aqui
     *  são pernas da mesma viagem. Nulo pra rota avulsa (o caso comum). */
    @Column(name = "viagem_id")
    private UUID viagemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Spec 14: só existe pra decidir, num cancelamento, se precisa avisar os passageiros
     *  de volta — "confirmado" não pode ser a última mensagem que eles receberam se a
     *  viagem não está mais de pé. */
    @Column(name = "passageiros_notificados", nullable = false)
    private boolean passageirosNotificados;

    protected RoutePlan() {
        // JPA
    }

    public RoutePlan(
            UUID tenantId, UUID gestorUserId, UUID driverId, UUID vehicleId,
            RouteCategoria categoria, LocalDate dataExecucao, BigDecimal valor, UUID viagemId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.gestorUserId = gestorUserId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.status = RoutePlanStatus.PLANEJADA;
        this.categoria = categoria;
        this.dataExecucao = dataExecucao;
        this.valor = valor;
        this.viagemId = viagemId;
        this.createdAt = Instant.now();
    }

    public void designarMotorista(UUID driverId) {
        this.driverId = driverId;
    }

    /** Spec 11, gap "edição de rota já atribuída" — só chamado por
     *  {@link RoutePlanService#update}, que já garante {@code status == PLANEJADA} antes. */
    public void editarPlanejamento(UUID vehicleId, LocalDate dataExecucao, BigDecimal valor) {
        this.vehicleId = vehicleId;
        this.dataExecucao = dataExecucao;
        this.valor = valor;
    }

    /** Chamado só na criação (RoutePlanService.create), nunca recalculado depois. */
    public void registrarCustoEstimado(BigDecimal custoEstimado, String pricingFormulaVersion) {
        this.custoEstimado = custoEstimado;
        this.pricingFormulaVersion = pricingFormulaVersion;
    }

    public void avancarStatus(RoutePlanStatus novo) {
        this.status = novo;
    }

    public void marcarPassageirosNotificados() {
        this.passageirosNotificados = true;
    }

    public boolean isPassageirosNotificados() {
        return passageirosNotificados;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGestorUserId() {
        return gestorUserId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public RoutePlanStatus getStatus() {
        return status;
    }

    public RouteCategoria getCategoria() {
        return categoria;
    }

    public LocalDate getDataExecucao() {
        return dataExecucao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getCustoEstimado() {
        return custoEstimado;
    }

    public String getPricingFormulaVersion() {
        return pricingFormulaVersion;
    }

    public UUID getViagemId() {
        return viagemId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
