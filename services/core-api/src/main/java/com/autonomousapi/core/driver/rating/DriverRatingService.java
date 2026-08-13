package com.autonomousapi.core.driver.rating;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.rating.dto.DriverRatingRequest;
import com.autonomousapi.core.driver.rating.dto.DriverRatingResponse;
import com.autonomousapi.core.driver.rating.dto.DriverRatingSummaryResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avaliação manual de motorista (spec 06, item 3). Toda leitura passa por
 * DriverRepository#findByIdAndTenantId primeiro — mesmo escopo por tenant do resto do
 * app — e o controller restringe TODOS os endpoints (inclusive leitura) a
 * GESTOR_FROTA/ADMIN: a regra do spec de nunca expor a nota ao próprio motorista não é
 * uma opção de tela, é ausência total de rota que um MOTORISTA autenticado possa chamar.
 */
@Service
public class DriverRatingService {

    private final DriverRepository drivers;
    private final DriverRatingManualRepository ratings;
    private final DriverRatingSummaryRepository summaries;

    public DriverRatingService(
            DriverRepository drivers,
            DriverRatingManualRepository ratings,
            DriverRatingSummaryRepository summaries) {
        this.drivers = drivers;
        this.ratings = ratings;
        this.summaries = summaries;
    }

    @Transactional
    public DriverRatingResponse create(JwtPrincipal principal, UUID driverId, DriverRatingRequest req) {
        Driver driver = findOwnedDriver(principal, driverId);
        DriverRatingManual rating =
                new DriverRatingManual(driver.getId(), principal.userId(), req.nota(), req.comentario());
        ratings.save(rating);
        recomputarResumo(driver.getId());
        return DriverRatingResponse.from(rating);
    }

    @Transactional(readOnly = true)
    public List<DriverRatingResponse> list(JwtPrincipal principal, UUID driverId) {
        Driver driver = findOwnedDriver(principal, driverId);
        return ratings.findAllByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
                .map(DriverRatingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverRatingSummaryResponse summary(JwtPrincipal principal, UUID driverId) {
        Driver driver = findOwnedDriver(principal, driverId);
        return summaries.findByDriverId(driver.getId())
                .map(s -> new DriverRatingSummaryResponse(driver.getId(), s.getNotaMedia(), s.getTotalAvaliacoes()))
                .orElseGet(() -> DriverRatingSummaryResponse.vazio(driver.getId()));
    }

    /**
     * Recomputa a média a partir de todas as notas do motorista. Volume por motorista é
     * baixo (avaliação manual, esporádica) — recalcular do zero a cada lançamento é mais
     * simples que manter contador incremental, e não é gargalo neste volume.
     */
    private void recomputarResumo(UUID driverId) {
        List<DriverRatingManual> todas = ratings.findAllByDriverIdOrderByCreatedAtDesc(driverId);
        BigDecimal media = todas.stream()
                .map(r -> BigDecimal.valueOf(r.getNota()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(todas.size()), 2, RoundingMode.HALF_UP);

        DriverRatingSummary resumo = summaries.findByDriverId(driverId)
                .orElseGet(() -> new DriverRatingSummary(driverId, media, todas.size()));
        resumo.atualizar(media, todas.size());
        summaries.save(resumo);
    }

    private Driver findOwnedDriver(JwtPrincipal principal, UUID driverId) {
        return drivers.findByIdAndTenantId(driverId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
    }
}
