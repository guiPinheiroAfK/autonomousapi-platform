package com.autonomousapi.core.driver.rating;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.geo.dto.DrivingEventsResponse;
import com.autonomousapi.core.trip.Trip;
import com.autonomousapi.core.trip.TripRepository;
import com.autonomousapi.core.trip.TripStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Avaliação automática de motorista (spec 06, item 3) — componente calculado a partir do
 * dado de condução (frenagem brusca, excesso de velocidade), via {@code driving-events}
 * do geo-api. Roda uma vez por dia, processa viagens finalizadas ainda não processadas
 * (ver {@code trip.rating_processed_at}) — nunca recalcula em tempo real.
 *
 * "Desvio de rota" fica de fora desta rodada: precisa de rota planejada como referência,
 * que depende do roteamento (spec 05, ainda não construído).
 */
@Component
public class DriverAutoRatingJob {

    private static final Logger logger = LoggerFactory.getLogger(DriverAutoRatingJob.class);

    /** Penalidade fixa por ocorrência — heurística v1, arbitrária e documentada, a
     * calibrar quando houver dado real pra saber quantas ocorrências por viagem já
     * indicam condução de risco (não necessariamente proporcional/linear). */
    private static final BigDecimal PENALIDADE_POR_OCORRENCIA = new BigDecimal("0.10");

    private final TripRepository trips;
    private final DriverRepository drivers;
    private final DriverRatingAutoRepository autoRatings;
    private final DriverRatingService driverRatingService;
    private final GeoApiClient geoApiClient;

    public DriverAutoRatingJob(
            TripRepository trips,
            DriverRepository drivers,
            DriverRatingAutoRepository autoRatings,
            DriverRatingService driverRatingService,
            GeoApiClient geoApiClient) {
        this.trips = trips;
        this.drivers = drivers;
        this.autoRatings = autoRatings;
        this.driverRatingService = driverRatingService;
        this.geoApiClient = geoApiClient;
    }

    /** Toda noite às 02:30 — depois do horário normal de viagens, antes do expediente. */
    @Scheduled(cron = "0 30 2 * * *")
    public void run() {
        int processadas = 0;
        for (Trip trip : trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA)) {
            try {
                processar(trip);
                processadas++;
            } catch (Exception ex) {
                logger.warn("Falha ao processar rating automático da viagem {}: {}", trip.getId(), ex.getMessage());
            }
        }
        logger.info("Rating automático: {} viagem(ns) processada(s)", processadas);
    }

    private void processar(Trip trip) {
        Optional<Driver> driverOpt = drivers.findByAppUserId(trip.getUserId());
        if (driverOpt.isEmpty() || trip.getEndedAt() == null) {
            // Sem motorista vinculado a login (dado legado) ou viagem sem fim registrado
            // — nada pra calcular, mas marca como processada pra não tentar de novo.
            trip.markRatingProcessed();
            trips.save(trip);
            return;
        }

        Driver driver = driverOpt.get();
        DrivingEventsResponse eventos = geoApiClient.drivingEvents(trip.getVehicleId(), trip.getStartedAt(), trip.getEndedAt());

        if (eventos.pingCount() > 0) {
            autoRatings.save(new DriverRatingAuto(
                    driver.getId(),
                    trip.getId(),
                    DriverRatingAuto.COMPONENTE_FRENAGEM_BRUSCA,
                    calcularScore(eventos.hardBrakingCount()),
                    trip.getStartedAt()));
            autoRatings.save(new DriverRatingAuto(
                    driver.getId(),
                    trip.getId(),
                    DriverRatingAuto.COMPONENTE_EXCESSO_VELOCIDADE,
                    calcularScore(eventos.overspeedCount()),
                    trip.getStartedAt()));
            driverRatingService.recomputarResumo(driver.getId());
        }

        trip.markRatingProcessed();
        trips.save(trip);
    }

    private static BigDecimal calcularScore(int ocorrencias) {
        BigDecimal score = BigDecimal.ONE.subtract(PENALIDADE_POR_OCORRENCIA.multiply(BigDecimal.valueOf(ocorrencias)));
        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
