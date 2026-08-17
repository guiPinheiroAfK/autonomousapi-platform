package com.autonomousapi.core.collectionpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Cadastro reutilizável de ponto de coleta/entrega (spec 02, spec 08 item 5) — evita
 *  redigitar o mesmo depósito/filial/cliente recorrente toda vez que uma rota é montada. */
@Entity
@Table(name = "collection_point")
public class CollectionPoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "endereco", nullable = false, length = 300)
    private String endereco;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "janela_inicio")
    private LocalTime janelaInicio;

    @Column(name = "janela_fim")
    private LocalTime janelaFim;

    @Column(name = "posicao_ajustada", nullable = false)
    private boolean posicaoAjustada;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CollectionPoint() {
        // JPA
    }

    public CollectionPoint(
            UUID tenantId, String nome, String endereco, double lat, double lon,
            LocalTime janelaInicio, LocalTime janelaFim) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.nome = nome;
        this.endereco = endereco;
        this.lat = lat;
        this.lon = lon;
        this.janelaInicio = janelaInicio;
        this.janelaFim = janelaFim;
        this.posicaoAjustada = false;
        this.ativo = true;
        this.createdAt = Instant.now();
    }

    /** Atualiza cadastro. Marca posicaoAjustada quando lat/lon mudam em relação ao valor
     *  salvo — sinaliza que o pino foi corrigido manualmente, não é mais o valor bruto do
     *  Nominatim. */
    public void atualizar(String nome, String endereco, double lat, double lon, LocalTime janelaInicio, LocalTime janelaFim) {
        if (this.lat != lat || this.lon != lon) {
            this.posicaoAjustada = true;
        }
        this.nome = nome;
        this.endereco = endereco;
        this.lat = lat;
        this.lon = lon;
        this.janelaInicio = janelaInicio;
        this.janelaFim = janelaFim;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void ativar() {
        this.ativo = true;
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

    public String getEndereco() {
        return endereco;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public LocalTime getJanelaInicio() {
        return janelaInicio;
    }

    public LocalTime getJanelaFim() {
        return janelaFim;
    }

    public boolean isPosicaoAjustada() {
        return posicaoAjustada;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
