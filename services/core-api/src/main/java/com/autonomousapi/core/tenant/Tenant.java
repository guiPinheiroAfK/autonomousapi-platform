package com.autonomousapi.core.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Empresa cliente (spec 03). Raiz de multi-tenancy do schema core. */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Margem sobre custo estimado, para valor sugerido de TRANSFER (spec 09) — configurável
     *  por tenant, não uma constante global da aplicação. Sem tela de edição dedicada ainda
     *  (specs/09), editável via UPDATE direto por enquanto. */
    @Column(name = "margem_padrao", nullable = false, precision = 5, scale = 4)
    private BigDecimal margemPadrao;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tenant() {
        // JPA
    }

    public Tenant(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.margemPadrao = new BigDecimal("0.20");
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMargemPadrao() {
        return margemPadrao;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
