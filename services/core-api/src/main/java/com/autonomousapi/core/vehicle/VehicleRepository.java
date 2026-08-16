package com.autonomousapi.core.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Escopo por tenant embutido na própria query — evita vazar veículo de outro tenant. */
    Optional<Vehicle> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPlateIgnoreCase(UUID tenantId, String plate);

    boolean existsByTenantIdAndPlateIgnoreCaseAndIdNot(UUID tenantId, String plate, UUID id);

    /**
     * Parênteses explícitos no OR — evitar o derived-query "TenantId And X Or Y" do
     * Spring Data, que o parser resolve como (TenantId AND X) OR Y e vazaria dado
     * de outro tenant quando Y for verdadeiro.
     */
    @Query("select v from Vehicle v where v.tenantId = :tenantId "
            + "and (v.proximaManutencaoData is not null or v.proximaManutencaoKm is not null)")
    List<Vehicle> findAllByTenantIdWithManutencaoAgendada(@Param("tenantId") UUID tenantId);

    /**
     * Cross-tenant de propósito: usado só pelo job diário de push (ADR 0016). Mesma query da
     * versão por tenant, sem o filtro — o job precisa varrer toda a base numa passada.
     */
    @Query("select v from Vehicle v where v.proximaManutencaoData is not null or v.proximaManutencaoKm is not null")
    List<Vehicle> findAllWithManutencaoAgendada();
}
