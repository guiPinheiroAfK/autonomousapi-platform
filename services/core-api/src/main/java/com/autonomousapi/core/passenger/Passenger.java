package com.autonomousapi.core.passenger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cadastro reutilizável de passageiro/cliente final (spec 14) — evita redigitar o mesmo
 *  contato toda vez que ele aparece numa viagem nova (comum em turismo, spec 13). Dado de
 *  terceiro sem consentimento direto (ver spec 14): exclusão é real, não soft-delete — não
 *  existe {@code ativo} aqui de propósito. */
@Entity
@Table(name = "passenger")
public class Passenger {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "telefone", nullable = false, length = 30)
    private String telefone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Passenger() {
        // JPA
    }

    public Passenger(UUID tenantId, String nome, String telefone) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.nome = nome;
        this.telefone = telefone;
        this.createdAt = Instant.now();
    }

    public void atualizar(String nome, String telefone) {
        this.nome = nome;
        this.telefone = telefone;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
