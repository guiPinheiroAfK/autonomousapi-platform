package com.autonomousapi.core.routeplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Uma parada de um {@link RoutePlan}. {@code ordemSugerida} é a ordem final confirmada
 *  pelo gestor na tela (a sugestão da heurística é revisável, nunca persistida sozinha). */
@Entity
@Table(name = "route_stop")
public class RouteStop {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "route_plan_id", nullable = false, updatable = false)
    private UUID routePlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10, updatable = false)
    private StopType tipo;

    @Column(name = "label", nullable = false, length = 300, updatable = false)
    private String label;

    @Column(name = "lat", nullable = false, updatable = false)
    private double lat;

    @Column(name = "lon", nullable = false, updatable = false)
    private double lon;

    @Column(name = "collection_point_id", updatable = false)
    private UUID collectionPointId;

    @Column(name = "passenger_id", updatable = false)
    private UUID passengerId;

    @Column(name = "janela_inicio")
    private LocalTime janelaInicio;

    @Column(name = "janela_fim")
    private LocalTime janelaFim;

    @Column(name = "ordem_sugerida", nullable = false, updatable = false)
    private int ordemSugerida;

    @Column(name = "ordem_real_executada")
    private Integer ordemRealExecutada;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    protected RouteStop() {
        // JPA
    }

    public RouteStop(
            UUID routePlanId,
            StopType tipo,
            String label,
            double lat,
            double lon,
            UUID collectionPointId,
            LocalTime janelaInicio,
            LocalTime janelaFim,
            int ordemSugerida,
            UUID passengerId) {
        this.id = UUID.randomUUID();
        this.routePlanId = routePlanId;
        this.tipo = tipo;
        this.label = label;
        this.lat = lat;
        this.lon = lon;
        this.collectionPointId = collectionPointId;
        this.janelaInicio = janelaInicio;
        this.janelaFim = janelaFim;
        this.ordemSugerida = ordemSugerida;
        this.passengerId = passengerId;
    }

    public boolean isConcluida() {
        return concluidaEm != null;
    }

    public void concluir(int ordemRealExecutada) {
        this.ordemRealExecutada = ordemRealExecutada;
        this.concluidaEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoutePlanId() {
        return routePlanId;
    }

    public StopType getTipo() {
        return tipo;
    }

    public String getLabel() {
        return label;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public UUID getCollectionPointId() {
        return collectionPointId;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public LocalTime getJanelaInicio() {
        return janelaInicio;
    }

    public LocalTime getJanelaFim() {
        return janelaFim;
    }

    public int getOrdemSugerida() {
        return ordemSugerida;
    }

    public Integer getOrdemRealExecutada() {
        return ordemRealExecutada;
    }

    public Instant getConcluidaEm() {
        return concluidaEm;
    }
}
