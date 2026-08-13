package com.autonomousapi.core.affiliate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Catálogo de parceiros de afiliado (spec 06, item 4) — dashcam, rastreador, etc. Não é
 * por tenant: é a AutonomousAPI quem negocia a parceria, todo tenant vê o mesmo catálogo.
 */
@Entity
@Table(name = "affiliate_partner")
public class AffiliatePartner {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "link_base", nullable = false, length = 500)
    private String linkBase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AffiliatePartner() {
        // JPA
    }

    public AffiliatePartner(String name, String category, String linkBase) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.category = category;
        this.linkBase = linkBase;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getLinkBase() {
        return linkBase;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
