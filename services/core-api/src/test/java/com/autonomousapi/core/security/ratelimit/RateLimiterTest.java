package com.autonomousapi.core.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final RateLimiter limiter = new RateLimiter(redis);

    private static final Duration JANELA = Duration.ofSeconds(60);

    @BeforeEach
    void ligarOpsForValue() {
        when(redis.opsForValue()).thenReturn(valores);
    }

    @Test
    void permiteEnquantoDentroDoLimite() {
        when(valores.increment("chave")).thenReturn(3L);

        assertTrue(limiter.tryAcquire("chave", 10, JANELA));
    }

    @Test
    void bloqueiaAoUltrapassarOLimite() {
        when(valores.increment("chave")).thenReturn(11L);

        assertFalse(limiter.tryAcquire("chave", 10, JANELA));
    }

    @Test
    void permiteExatamenteNoLimite() {
        when(valores.increment("chave")).thenReturn(10L);

        assertTrue(limiter.tryAcquire("chave", 10, JANELA), "o décimo ainda está dentro de 'até 10'");
    }

    @Test
    void defineTtlApenasNaPrimeiraTentativaDaJanela() {
        when(valores.increment("chave")).thenReturn(1L);
        limiter.tryAcquire("chave", 10, JANELA);
        verify(redis).expire("chave", JANELA);
    }

    @Test
    void naoRenovaTtlNasTentativasSeguintes() {
        // Se cada tentativa reiniciasse o TTL, um atacante contínuo empurraria a expiração
        // para sempre e o bloqueio nunca terminaria (ou nunca recomeçaria a contagem).
        when(valores.increment("chave")).thenReturn(7L);

        limiter.tryAcquire("chave", 10, JANELA);

        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    void liberaRequisicaoQuandoRedisEstaForaDoAr() {
        // Decisão explícita: rate limit é proteção acessória. Redis fora não pode virar
        // login fora — seria trocar risco de abuso por indisponibilidade certa.
        when(valores.increment("chave")).thenThrow(new RedisConnectionFailureException("sem conexão"));

        assertTrue(limiter.tryAcquire("chave", 10, JANELA));
    }
}
