package com.autonomousapi.core.workorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ordem de serviço de manutenção/oficina. Substitui o mock que vivia só no front
 * (apps/web/src/data/ordensServico.ts). Referências a veículo/motorista são por UUID
 * cru, sem @ManyToOne — mesmo padrão do resto do schema `core` (ver VehicleCostEntry):
 * o escopo por tenant é sempre resolvido pela aplicação, não pelo grafo JPA.
 */
@Entity
@Table(name = "work_order")
public class WorkOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private WorkOrderType tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 10)
    private WorkOrderPriority prioridade;

    @Column(name = "descricao_problema", nullable = false, length = 1000)
    private String descricaoProblema;

    @Column(name = "observacoes", length = 1000)
    private String observacoes;

    @Column(name = "responsavel_oficina", nullable = false, length = 150)
    private String responsavelOficina;

    @Column(name = "data_abertura", nullable = false)
    private LocalDate dataAbertura;

    @Column(name = "previsao_conclusao", nullable = false)
    private LocalDate previsaoConclusao;

    @Column(name = "km_abertura", nullable = false)
    private int kmAbertura;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkOrder() {
        // JPA
    }

    public WorkOrder(UUID tenantId, UUID vehicleId, String numero) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.vehicleId = vehicleId;
        this.numero = numero;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            UUID vehicleId,
            UUID driverId,
            WorkOrderType tipo,
            WorkOrderStatus status,
            WorkOrderPriority prioridade,
            String descricaoProblema,
            String observacoes,
            String responsavelOficina,
            LocalDate dataAbertura,
            LocalDate previsaoConclusao,
            int kmAbertura) {
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.tipo = tipo;
        this.status = status;
        this.prioridade = prioridade;
        this.descricaoProblema = descricaoProblema;
        this.observacoes = observacoes;
        this.responsavelOficina = responsavelOficina;
        this.dataAbertura = dataAbertura;
        this.previsaoConclusao = previsaoConclusao;
        this.kmAbertura = kmAbertura;
        this.updatedAt = Instant.now();
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

    public UUID getDriverId() {
        return driverId;
    }

    public String getNumero() {
        return numero;
    }

    public WorkOrderType getTipo() {
        return tipo;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public WorkOrderPriority getPrioridade() {
        return prioridade;
    }

    public String getDescricaoProblema() {
        return descricaoProblema;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getResponsavelOficina() {
        return responsavelOficina;
    }

    public LocalDate getDataAbertura() {
        return dataAbertura;
    }

    public LocalDate getPrevisaoConclusao() {
        return previsaoConclusao;
    }

    public int getKmAbertura() {
        return kmAbertura;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
