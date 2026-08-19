package com.autonomousapi.core.expense;

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
 * Despesa categorizada (spec 10) — evolução de {@code vehicle_cost_entry} (migration V23):
 * mesma tabela lógica, tenant_id nativo (antes o isolamento vinha só de vehicle_id),
 * vehicleId agora nullable (despesa de frota, ex. seguro corporativo) e categorias
 * expandidas de 3 para 8. litrosOuKwh/odometro só fazem sentido com categoria=COMBUSTIVEL
 * (travado também por CHECK no banco, migration V23).
 */
@Entity
@Table(name = "expense_entry")
public class ExpenseEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private ExpenseCategory categoria;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte", nullable = false, length = 20)
    private ExpenseSource fonte;

    @Column(name = "litros_ou_kwh", precision = 10, scale = 3)
    private BigDecimal litrosOuKwh;

    @Column(name = "odometro")
    private Integer odometro;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpenseEntry() {
        // JPA
    }

    public ExpenseEntry(
            UUID tenantId, UUID vehicleId, ExpenseCategory categoria, BigDecimal valor,
            String descricao, LocalDate data, BigDecimal litrosOuKwh, Integer odometro) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.vehicleId = vehicleId;
        this.categoria = categoria;
        this.valor = valor;
        this.descricao = descricao;
        this.data = data;
        this.fonte = ExpenseSource.MANUAL;
        this.litrosOuKwh = categoria == ExpenseCategory.COMBUSTIVEL ? litrosOuKwh : null;
        this.odometro = categoria == ExpenseCategory.COMBUSTIVEL ? odometro : null;
        this.createdAt = Instant.now();
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

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public LocalDate getData() {
        return data;
    }

    public ExpenseSource getFonte() {
        return fonte;
    }

    public BigDecimal getLitrosOuKwh() {
        return litrosOuKwh;
    }

    public Integer getOdometro() {
        return odometro;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
