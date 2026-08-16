package com.autonomousapi.core.driver.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.rating.dto.DriverRatingRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DriverRatingServiceTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final DriverRatingManualRepository manualRatings = mock(DriverRatingManualRepository.class);
    private final DriverRatingAutoRepository autoRatings = mock(DriverRatingAutoRepository.class);
    private final DriverRatingSummaryRepository summaries = mock(DriverRatingSummaryRepository.class);

    private final DriverRatingService service =
            new DriverRatingService(drivers, manualRatings, autoRatings, summaries);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void resumoUsaSoManualQuandoNaoHaAutomatica() {
        UUID driverId = UUID.randomUUID();
        when(manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driverId))
                .thenReturn(List.of(rating(driverId, (short) 4), rating(driverId, (short) 2)));
        when(autoRatings.findAllByDriverId(driverId)).thenReturn(List.of());
        when(summaries.findByDriverId(driverId)).thenReturn(Optional.empty());

        service.recomputarResumo(driverId);

        ArgumentCaptor<DriverRatingSummary> captor = ArgumentCaptor.forClass(DriverRatingSummary.class);
        verify(summaries).save(captor.capture());
        assertEquals(new BigDecimal("3.00"), captor.getValue().getNotaMedia());
        assertEquals(2, captor.getValue().getTotalAvaliacoes());
    }

    @Test
    void resumoUsaSoAutomaticaQuandoNaoHaManual() {
        UUID driverId = UUID.randomUUID();
        when(manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driverId)).thenReturn(List.of());
        when(autoRatings.findAllByDriverId(driverId))
                .thenReturn(List.of(
                        auto(driverId, new BigDecimal("1.00")), auto(driverId, new BigDecimal("0.50"))));
        when(summaries.findByDriverId(driverId)).thenReturn(Optional.empty());

        service.recomputarResumo(driverId);

        ArgumentCaptor<DriverRatingSummary> captor = ArgumentCaptor.forClass(DriverRatingSummary.class);
        verify(summaries).save(captor.capture());
        // média automática 0.75 * 5 = 3.75
        assertEquals(new BigDecimal("3.75"), captor.getValue().getNotaMedia());
        assertEquals(0, captor.getValue().getTotalAvaliacoes());
    }

    @Test
    void resumoCombinaManualEAutomaticaComPesoMaiorNoManual() {
        UUID driverId = UUID.randomUUID();
        when(manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driverId))
                .thenReturn(List.of(rating(driverId, (short) 5)));
        when(autoRatings.findAllByDriverId(driverId)).thenReturn(List.of(auto(driverId, BigDecimal.ZERO)));
        when(summaries.findByDriverId(driverId)).thenReturn(Optional.empty());

        service.recomputarResumo(driverId);

        ArgumentCaptor<DriverRatingSummary> captor = ArgumentCaptor.forClass(DriverRatingSummary.class);
        verify(summaries).save(captor.capture());
        // manual=5 (peso 0.7) + auto=0*5=0 (peso 0.3) = 3.50
        assertEquals(new BigDecimal("3.50"), captor.getValue().getNotaMedia());
    }

    @Test
    void resumoNaoGravaNadaSemNenhumaFonte() {
        UUID driverId = UUID.randomUUID();
        when(manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driverId)).thenReturn(List.of());
        when(autoRatings.findAllByDriverId(driverId)).thenReturn(List.of());

        service.recomputarResumo(driverId);

        verify(summaries, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createRecomputaResumoAposLancarAvaliacaoManual() {
        Driver driver = new Driver(tenantId, "Fulano", "12345678901", null);
        when(drivers.findByIdAndTenantId(driver.getId(), tenantId)).thenReturn(Optional.of(driver));
        when(manualRatings.findAllByDriverIdOrderByCreatedAtDesc(driver.getId()))
                .thenReturn(List.of(rating(driver.getId(), (short) 5)));
        when(autoRatings.findAllByDriverId(driver.getId())).thenReturn(List.of());
        when(summaries.findByDriverId(driver.getId())).thenReturn(Optional.empty());

        service.create(principal, driver.getId(), new DriverRatingRequest((short) 5, "ótimo"));

        verify(manualRatings).save(any());
        verify(summaries).save(any());
    }

    @Test
    void summaryVazioQuandoMotoristaNuncaFoiAvaliado() {
        UUID driverId = UUID.randomUUID();
        Driver driver = new Driver(tenantId, "Fulano", "12345678901", null);
        when(drivers.findByIdAndTenantId(driverId, tenantId)).thenReturn(Optional.of(driver));
        when(summaries.findByDriverId(driver.getId())).thenReturn(Optional.empty());

        var resumo = service.summary(principal, driverId);

        assertNull(resumo.notaMedia());
        assertEquals(0, resumo.totalAvaliacoes());
    }

    private static DriverRatingManual rating(UUID driverId, short nota) {
        return new DriverRatingManual(driverId, UUID.randomUUID(), nota, null);
    }

    private static DriverRatingAuto auto(UUID driverId, BigDecimal score) {
        return new DriverRatingAuto(
                driverId, UUID.randomUUID(), DriverRatingAuto.COMPONENTE_FRENAGEM_BRUSCA, score, Instant.now());
    }
}
