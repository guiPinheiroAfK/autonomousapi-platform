package com.autonomousapi.core.driver;

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
 * Cadastro operacional do motorista da frota (spec 05, Fase 1). NÃO é a conta de
 * login (isso é {@code core.app_user}, role MOTORISTA) — vincular um motorista a
 * um usuário com login é feature futura (fluxo de convite).
 */
@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "cnh", nullable = false, length = 11)
    private String cnh;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverStatus status;

    /** Opcional: sem valor, o motorista não entra no alerta de CNH vencendo. */
    @Column(name = "cnh_validade")
    private LocalDate cnhValidade;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Driver() {
        // JPA
    }

    public Driver(UUID tenantId, String name, String cnh, String phone) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.cnh = cnh;
        this.phone = phone;
        this.status = DriverStatus.ATIVO;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String cnh, String phone, DriverStatus status, LocalDate cnhValidade) {
        this.name = name;
        this.cnh = cnh;
        this.phone = phone;
        this.status = status;
        this.cnhValidade = cnhValidade;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getCnh() {
        return cnh;
    }

    public String getPhone() {
        return phone;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public LocalDate getCnhValidade() {
        return cnhValidade;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
