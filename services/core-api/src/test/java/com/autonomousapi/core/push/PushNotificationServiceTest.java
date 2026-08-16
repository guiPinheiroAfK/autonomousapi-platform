package com.autonomousapi.core.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PushNotificationServiceTest {

    private final PushDeviceTokenRepository repo = mock(PushDeviceTokenRepository.class);
    private final PushSender sender = mock(PushSender.class);
    private final PushNotificationService service = new PushNotificationService(repo, sender);

    @Test
    void registerDeviceCriaQuandoTokenNovo() {
        UUID userId = UUID.randomUUID();
        when(repo.findByToken("tok-1")).thenReturn(Optional.empty());

        service.registerDevice(userId, "tok-1", "ANDROID");

        verify(repo).save(any(PushDeviceToken.class));
    }

    @Test
    void registerDeviceReatribuiQuandoTokenJaExiste() {
        UUID donoAntigo = UUID.randomUUID();
        UUID donoNovo = UUID.randomUUID();
        PushDeviceToken existente = new PushDeviceToken(donoAntigo, "tok-1", "ANDROID");
        when(repo.findByToken("tok-1")).thenReturn(Optional.of(existente));

        service.registerDevice(donoNovo, "tok-1", "IOS");

        assertEquals(donoNovo, existente.getUserId());
        assertEquals("IOS", existente.getPlataforma());
        verify(repo, never()).save(any());
    }

    @Test
    void notifyUserEnviaParaTodosOsAparelhosDoUsuario() {
        UUID userId = UUID.randomUUID();
        when(repo.findAllByUserId(userId)).thenReturn(List.of(
                new PushDeviceToken(userId, "tok-a", "ANDROID"),
                new PushDeviceToken(userId, "tok-b", "IOS")));

        service.notifyUser(userId, "Título", "Corpo");

        verify(sender, times(2)).sendToToken(any(), eq("Título"), eq("Corpo"));
    }

    @Test
    void notifyUserSemAparelhoNaoChamaOSender() {
        UUID userId = UUID.randomUUID();
        when(repo.findAllByUserId(userId)).thenReturn(List.of());

        service.notifyUser(userId, "Título", "Corpo");

        verify(sender, never()).sendToToken(any(), any(), any());
    }
}
