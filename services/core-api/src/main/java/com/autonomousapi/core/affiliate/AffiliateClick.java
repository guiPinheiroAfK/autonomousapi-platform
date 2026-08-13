package com.autonomousapi.core.affiliate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Clique registrado num parceiro de afiliado (spec 06, item 4) — métrica de conversão.
 * Carrega tenant_id direto (diferente do resto do schema `core`): não tem um "pai" já
 * escopado por tenant para herdar o isolamento, é ação direta do usuário.
 *
 * SEM affiliate_conversion ainda: spec é explícito — "não assumir que clique = venda".
 * Essa tabela entra quando existir o primeiro parceiro real com webhook de conversão.
 */
@Entity
@Table(name = "affiliate_click")
public class AffiliateClick {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private UUID partnerId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AffiliateClick() {
        // JPA
    }

    public AffiliateClick(UUID tenantId, UUID partnerId, UUID vehicleId, UUID userId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.partnerId = partnerId;
        this.vehicleId = vehicleId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPartnerId() {
        return partnerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
