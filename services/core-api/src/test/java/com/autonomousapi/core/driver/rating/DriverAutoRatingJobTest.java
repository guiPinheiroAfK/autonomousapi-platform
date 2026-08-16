package com.autonomousapi.core.driver.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.geo.dto.DrivingEventsResponse;
import com.autonomousapi.core.trip.Trip;
import com.autonomousapi.core.trip.TripRepository;
import com.autonomousapi.core.trip.TripStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DriverAutoRatingJobTest {

    private final TripRepository trips = mock(TripRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final DriverRatingAutoRepository autoRatings = mock(DriverRatingAutoRepository.class);
    private final DriverRatingService driverRatingService = mock(DriverRatingService.class);
    private final GeoApiClient geoApiClient = mock(GeoApiClient.class);

    private final DriverAutoRatingJob job =
            new DriverAutoRatingJob(trips, drivers, autoRatings, driverRatingService, geoApiClient);

    private static Trip viagemFinalizada(UUID tenantId, UUID userId, UUID vehicleId) {
        Trip trip = new Trip(tenantId, userId, vehicleId);
        trip.finish();
        return trip;
    }

    @Test
    void lancaDoisComponentesEQuandoHaAmostraSuficiente() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Trip trip = viagemFinalizada(UUID.randomUUID(), userId, vehicleId);
        Driver driver = new Driver(UUID.randomUUID(), "Fulano", "12345678901", null);
        driver.linkAppUser(userId);

        when(trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA)).thenReturn(List.of(trip));
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.of(driver));
        when(geoApiClient.drivingEvents(eq(vehicleId), any(), any()))
                .thenReturn(new DrivingEventsResponse(50, 2, 1));

        job.run();

        ArgumentCaptor<DriverRatingAuto> captor = ArgumentCaptor.forClass(DriverRatingAuto.class);
        verify(autoRatings, times(2)).save(captor.capture());
        List<DriverRatingAuto> salvos = captor.getAllValues();

        DriverRatingAuto frenagem = salvos.stream()
                .filter(r -> r.getComponente().equals(DriverRatingAuto.COMPONENTE_FRENAGEM_BRUSCA))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("0.80"), frenagem.getScore()); // 1 - 2*0.10

        DriverRatingAuto velocidade = salvos.stream()
                .filter(r -> r.getComponente().equals(DriverRatingAuto.COMPONENTE_EXCESSO_VELOCIDADE))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("0.90"), velocidade.getScore()); // 1 - 1*0.10

        verify(driverRatingService).recomputarResumo(driver.getId());
        assertNotNull(trip.getRatingProcessedAt());
        verify(trips).save(trip);
    }

    @Test
    void scoreNuncaFicaNegativoComMuitasOcorrencias() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Trip trip = viagemFinalizada(UUID.randomUUID(), userId, vehicleId);
        Driver driver = new Driver(UUID.randomUUID(), "Fulano", "12345678901", null);
        driver.linkAppUser(userId);

        when(trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA)).thenReturn(List.of(trip));
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.of(driver));
        when(geoApiClient.drivingEvents(eq(vehicleId), any(), any()))
                .thenReturn(new DrivingEventsResponse(50, 20, 0));

        job.run();

        ArgumentCaptor<DriverRatingAuto> captor = ArgumentCaptor.forClass(DriverRatingAuto.class);
        verify(autoRatings, times(2)).save(captor.capture());
        DriverRatingAuto frenagem = captor.getAllValues().stream()
                .filter(r -> r.getComponente().equals(DriverRatingAuto.COMPONENTE_FRENAGEM_BRUSCA))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), frenagem.getScore());
    }

    @Test
    void naoLancaRatingQuandoAmostraInsuficiente() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Trip trip = viagemFinalizada(UUID.randomUUID(), userId, vehicleId);
        Driver driver = new Driver(UUID.randomUUID(), "Fulano", "12345678901", null);
        driver.linkAppUser(userId);

        when(trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA)).thenReturn(List.of(trip));
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.of(driver));
        when(geoApiClient.drivingEvents(eq(vehicleId), any(), any())).thenReturn(DrivingEventsResponse.vazio());

        job.run();

        verify(autoRatings, never()).save(any());
        verify(driverRatingService, never()).recomputarResumo(any());
        assertNotNull(trip.getRatingProcessedAt());
    }

    @Test
    void marcaProcessadaSemChamarGeoApiQuandoMotoristaNaoTemLogin() {
        UUID userId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Trip trip = viagemFinalizada(UUID.randomUUID(), userId, vehicleId);

        when(trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA)).thenReturn(List.of(trip));
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.empty());

        job.run();

        verify(geoApiClient, never()).drivingEvents(any(), any(), any());
        assertNotNull(trip.getRatingProcessedAt());
    }

    @Test
    void umaViagemComFalhaNaoImpedeAsDemais() {
        UUID userIdComErro = UUID.randomUUID();
        UUID userIdOk = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Trip comErro = viagemFinalizada(UUID.randomUUID(), userIdComErro, vehicleId);
        Trip ok = viagemFinalizada(UUID.randomUUID(), userIdOk, vehicleId);
        Driver driverOk = new Driver(UUID.randomUUID(), "Fulano", "12345678901", null);
        driverOk.linkAppUser(userIdOk);

        when(trips.findAllByStatusAndRatingProcessedAtIsNull(TripStatus.FINALIZADA))
                .thenReturn(List.of(comErro, ok));
        when(drivers.findByAppUserId(userIdComErro)).thenThrow(new RuntimeException("boom"));
        when(drivers.findByAppUserId(userIdOk)).thenReturn(Optional.of(driverOk));
        when(geoApiClient.drivingEvents(eq(vehicleId), any(), any()))
                .thenReturn(new DrivingEventsResponse(10, 0, 0));

        job.run();

        assertNotNull(ok.getRatingProcessedAt());
        verify(driverRatingService).recomputarResumo(driverOk.getId());
    }
}
