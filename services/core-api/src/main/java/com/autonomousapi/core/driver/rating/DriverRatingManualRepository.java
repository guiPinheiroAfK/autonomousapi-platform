package com.autonomousapi.core.driver.rating;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRatingManualRepository extends JpaRepository<DriverRatingManual, UUID> {

    /** Sem paginação — usado só por {@code recomputarResumo}, que precisa de todas as
     *  avaliações pra calcular a média (não é uma tela de listagem). */
    List<DriverRatingManual> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId);

    /** Paginado (cleanup de performance) — a tela de listagem (`GET .../ratings`) não
     *  precisa do histórico inteiro de uma vez. */
    Page<DriverRatingManual> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    /** Escopa a exclusão ao motorista do path — id de avaliação de outro motorista (mesmo
     *  que do mesmo tenant) nunca é encontrado por aqui. */
    Optional<DriverRatingManual> findByIdAndDriverId(UUID id, UUID driverId);
}
