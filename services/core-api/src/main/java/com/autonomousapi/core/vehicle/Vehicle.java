package com.autonomousapi.core.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Veículo da frota de um tenant (spec 05, Fase 1). */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plate", nullable = false, length = 10)
    private String plate;

    @Column(name = "brand", nullable = false, length = 80)
    private String brand;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(name = "odometer_km", nullable = false)
    private int odometerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VehicleStatus status;

    /** Opcionais: sem valor, o veículo não entra em nenhum alerta de manutenção. */
    @Column(name = "proxima_manutencao_data")
    private LocalDate proximaManutencaoData;

    @Column(name = "proxima_manutencao_km")
    private Integer proximaManutencaoKm;

    /**
     * Atributos que variam por tipo de veículo (ADR 0008): autonomia e conector num elétrico,
     * cilindrada numa moto, valor FIPE, histórico de sinistro. Nunca guardar aqui o que é
     * filtrado ou ordenado em listagem — isso continua sendo coluna de verdade.
     *
     * LinkedHashMap por padrão para a ordem das chaves não dançar entre uma escrita e outra,
     * o que deixa o diff no banco legível.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos", nullable = false)
    private Map<String, Object> atributos = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
        // JPA
    }

    public Vehicle(UUID tenantId, String plate, String brand, String model,
            Integer modelYear, int odometerKm) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.plate = plate;
        this.brand = brand;
        this.model = model;
        this.modelYear = modelYear;
        this.odometerKm = odometerKm;
        this.status = VehicleStatus.ATIVO;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String plate, String brand, String model, Integer modelYear,
            int odometerKm, VehicleStatus status, LocalDate proximaManutencaoData,
            Integer proximaManutencaoKm, Map<String, Object> atributos) {
        this.plate = plate;
        this.brand = brand;
        this.model = model;
        this.modelYear = modelYear;
        this.odometerKm = odometerKm;
        this.status = status;
        this.proximaManutencaoData = proximaManutencaoData;
        this.proximaManutencaoKm = proximaManutencaoKm;
        // Cópia defensiva: sem isso o mapa do request continuaria ligado à entidade e uma
        // alteração posterior nele vazaria para dentro do agregado sem passar por update().
        this.atributos = atributos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(atributos);
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPlate() {
        return plate;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Integer getModelYear() {
        return modelYear;
    }

    public int getOdometerKm() {
        return odometerKm;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public LocalDate getProximaManutencaoData() {
        return proximaManutencaoData;
    }

    public Integer getProximaManutencaoKm() {
        return proximaManutencaoKm;
    }

    /** Cópia não modificável: alterar atributo passa por {@link #update}, nunca pelo getter. */
    public Map<String, Object> getAtributos() {
        return atributos == null ? Map.of() : Map.copyOf(atributos);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
