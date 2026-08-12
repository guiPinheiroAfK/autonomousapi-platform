package com.autonomousapi.core.security.ratelimit;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Contador de tentativas por janela fixa, no Redis.
 *
 * Por que Redis e não memória: o contador precisa valer para o serviço inteiro, não por
 * instância. Com o contador em memória, subir uma segunda réplica multiplica o limite
 * efetivo pelo número de réplicas — e um atacante só precisa distribuir as tentativas
 * entre elas (ADR 0007).
 *
 * Decisão importante de disponibilidade: se o Redis estiver fora ou lento, este limitador
 * LIBERA a requisição em vez de bloquear. Rate limit é proteção acessória; deixar o login
 * inteiro cair porque o Redis reiniciou seria trocar um risco de abuso por uma queda certa.
 * Em compensação, a falha é logada como warning para não passar despercebida.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Incrementa o contador da chave e diz se ainda está dentro do limite.
     *
     * Janela fixa (não deslizante): simples e suficiente aqui. O pior caso é permitir
     * até 2x o limite na virada de duas janelas — irrelevante para conter força bruta,
     * e evita a complexidade de manter um sorted set por chave.
     *
     * @return true se a requisição deve ser permitida
     */
    public boolean tryAcquire(String chave, int maxNaJanela, Duration janela) {
        try {
            Long contagem = redis.opsForValue().increment(chave);
            if (contagem == null) {
                return true;
            }
            // Só o primeiro da janela define o TTL; os demais não podem reiniciá-lo,
            // senão um atacante contínuo empurraria a expiração para sempre.
            if (contagem == 1L) {
                redis.expire(chave, janela);
            }
            return contagem <= maxNaJanela;
        } catch (RuntimeException ex) {
            log.warn("Rate limit indisponível (Redis): {}. Requisição liberada.", ex.getMessage());
            return true;
        }
    }
}
