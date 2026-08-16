package com.autonomousapi.core.push;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de device token + disparo de notificação por usuário (ADR 0016). Ponto único
 * onde "usuário" vira "um ou mais device tokens" — quem dispara eventos não precisa saber
 * disso.
 */
@Service
public class PushNotificationService {

    private final PushDeviceTokenRepository deviceTokens;
    private final PushSender pushSender;

    public PushNotificationService(PushDeviceTokenRepository deviceTokens, PushSender pushSender) {
        this.deviceTokens = deviceTokens;
        this.pushSender = pushSender;
    }

    /** Upsert por token: mesmo aparelho pode trocar de usuário (ex. outro motorista logou). */
    @Transactional
    public void registerDevice(UUID userId, String token, String plataforma) {
        deviceTokens.findByToken(token)
                .ifPresentOrElse(
                        existing -> existing.reassign(userId, plataforma),
                        () -> deviceTokens.save(new PushDeviceToken(userId, token, plataforma)));
    }

    /** Envia para todos os aparelhos registrados do usuário. Sem aparelho, é no-op silencioso. */
    @Transactional(readOnly = true)
    public void notifyUser(UUID userId, String title, String body) {
        deviceTokens.findAllByUserId(userId)
                .forEach(d -> pushSender.sendToToken(d.getToken(), title, body));
    }
}
