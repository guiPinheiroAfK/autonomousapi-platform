package com.autonomousapi.core.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Preço de referência de combustível/energia por tenant (spec 09) — mesmo tratamento manual
 * já dado à FIPE (spec 06, item 2). {@code tipoCombustivel} usa os mesmos valores da chave
 * {@code combustivel} em {@code vehicle.atributos} (ex. "flex", "diesel", "eletrico") — o
 * preço de "eletrico" é por kWh, os demais por litro.
 */
@Entity
@Table(name = "fuel_price_reference")
public class FuelPriceReference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tipo_combustivel", nullable = false, length = 20, updatable = false)
    private String tipoCombustivel;

    @Column(name = "preco", nullable = false, precision = 8, scale = 3)
    private BigDecimal preco;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDate dataAtualizacao;

    protected FuelPriceReference() {
        // JPA
    }

    public FuelPriceReference(UUID tenantId, String tipoCombustivel, BigDecimal preco, LocalDate dataAtualizacao) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.tipoCombustivel = tipoCombustivel;
        this.preco = preco;
        this.dataAtualizacao = dataAtualizacao;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTipoCombustivel() {
        return tipoCombustivel;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public LocalDate getDataAtualizacao() {
        return dataAtualizacao;
    }
}
