package com.autonomousapi.core.driver.rating;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.rating.dto.DriverRatingRequest;
import com.autonomousapi.core.driver.rating.dto.DriverRatingResponse;
import com.autonomousapi.core.driver.rating.dto.DriverRatingSummaryResponse;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avaliação de motorista, manual (spec 06, item 3) e automática (mesmo item, componente
 * calculado por {@link DriverAutoRatingJob} a partir do dado de condução). Toda leitura
 * passa por DriverRepository#findByIdAndTenantId primeiro — mesmo escopo por tenant do
 * resto do app — e o controller restringe TODOS os endpoints (inclusive leitura) a
 * GESTOR_FROTA/ADMIN: a regra do spec de nunca expor a nota ao próprio motorista não é
 * uma opção de tela, é ausência total de rota que um MOTORISTA autenticado possa chamar.
 */
@Service
public class DriverRatingService {

    /**
     * Peso do componente manual no blend com o automático — arbitrário e documentado
     * como tal (spec 06 não define a fórmula, só pede "uma nota única"). Maior peso pro
     * manual porque é avaliação humana direta; a calibrar quando houver dado real de
     * quanto o componente automático se correlaciona com desempenho de verdade.
     */
    private static final BigDecimal PESO_MANUAL = new BigDecimal("0.7");
    private static final BigDecimal PESO_AUTO = new BigDecimal("0.3");
    private static final BigDecimal ESCALA_AUTO_PARA_NOTA = BigDecimal.valueOf(5);

    private final DriverRepository drivers;
    private final DriverRatingManualRepository manualRatings;
    private final DriverRatingAutoRepository autoRatings;
    private final DriverRatingSummaryRepository summaries;

    public DriverRatingService(
            DriverRepository drivers,
            DriverRatingManualRepository manualRatings,
            DriverRatingAutoRepository autoRatings,
            DriverRatingSummaryRepository summaries) {
        this.drivers = drivers;
        this.manualRatings = manualRatings;
        this.autoRatings = autoRatings;
        this.summaries = summaries;
    }

    @Transactional
    public DriverRatingResponse create(JwtPrincipal principal, UUID driverId, DriverRatingRequest req) {
        Driver driver = findOwnedDriver(principal, driverId);
        DriverRatingManual rating =
                new DriverRatingManual(driver.getId(), principal.userId(), req.nota(), req.comentario());
        manualRatings.save(rating);
        recomputarResumo(driver.getId());
        return DriverRatingResponse.from(rating);
    }

    @Transactional(readOnly = true)
    public List<DriverRatingResponse> list(JwtPrincipal principal, UUID driverId) {
        Driver driver = findOwnedDriver(principal, driverId);
        return manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
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
     * Recomputa o resumo combinando manual (1-5) e automático (0-1, escalado pra 1-5) —
     * chamado tanto por {@link #create} quanto por {@link DriverAutoRatingJob}, pra nunca
     * ter as duas fontes divergindo sobre qual é "o" resumo atual do motorista. Sem
     * nenhuma das duas fontes, não grava nada (resumo "vazio" já é o comportamento padrão
     * de leitura, ver {@link #summary}). Só uma fonte presente = usa ela sozinha, sem
     * diluir com peso de um componente que não existe ainda.
     *
     * Volume por motorista é baixo — recalcular do zero a cada lançamento é mais simples
     * que manter contador incremental, e não é gargalo neste volume.
     */
    @Transactional
    public void recomputarResumo(UUID driverId) {
        List<DriverRatingManual> manuais = manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driverId);
        List<DriverRatingAuto> automaticas = autoRatings.findAllByDriverId(driverId);

        if (manuais.isEmpty() && automaticas.isEmpty()) {
            return;
        }

        BigDecimal mediaManual = manuais.isEmpty()
                ? null
                : media(manuais.stream().map(r -> BigDecimal.valueOf(r.getNota())).toList(), 2);
        BigDecimal mediaAutoEscalada = automaticas.isEmpty()
                ? null
                : media(automaticas.stream().map(DriverRatingAuto::getScore).toList(), 4)
                        .multiply(ESCALA_AUTO_PARA_NOTA)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal notaFinal;
        if (mediaManual != null && mediaAutoEscalada != null) {
            notaFinal = mediaManual
                    .multiply(PESO_MANUAL)
                    .add(mediaAutoEscalada.multiply(PESO_AUTO))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            notaFinal = mediaManual != null ? mediaManual : mediaAutoEscalada;
        }

        // total_avaliacoes conta só lançamentos manuais — é o número que a tela do
        // gestor já mostra como "quantas vezes você avaliou", não muda de significado
        // por causa do componente automático entrar no cálculo da nota.
        DriverRatingSummary resumo = summaries.findByDriverId(driverId)
                .orElseGet(() -> new DriverRatingSummary(driverId, notaFinal, manuais.size()));
        resumo.atualizar(notaFinal, manuais.size());
        summaries.save(resumo);
    }

    private static BigDecimal media(List<BigDecimal> valores, int escala) {
        return valores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valores.size()), escala, RoundingMode.HALF_UP);
    }

    private Driver findOwnedDriver(JwtPrincipal principal, UUID driverId) {
        return Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, principal.tenantId()), "Motorista não encontrado.");
    }
}
