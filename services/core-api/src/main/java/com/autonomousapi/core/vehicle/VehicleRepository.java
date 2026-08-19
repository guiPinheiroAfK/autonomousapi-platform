package com.autonomousapi.core.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Busca/filtro server-side (placa/marca/modelo + status) combinados com a paginação —
     * antes da paginação, /v1/vehicles devolvia a frota inteira e o front filtrava em memória;
     * paginar sem isso deixaria a busca "sumir" com veículo que está em outra página. {@code
     * search}/{@code status} nulos = sem filtro (mesmo padrão null-safe já usado em
     * ExpenseEntryRepository.findFleetExpenses).
     */
    @Query("select v from Vehicle v where v.tenantId = :tenantId "
            + "and (:status is null or v.status = :status) "
            + "and (:search is null or lower(v.plate) like :search "
            + "     or lower(v.brand) like :search or lower(v.model) like :search) "
            + "order by v.createdAt desc")
    Page<Vehicle> search(
            @Param("tenantId") UUID tenantId,
            @Param("search") String searchLikePattern,
            @Param("status") VehicleStatus status,
            Pageable pageable);

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

    /** Cross-tenant de propósito: usado só pelo job semanal de sincronização FIPE. */
    List<Vehicle> findAllByTipoIn(List<VehicleType> tipos);
}
