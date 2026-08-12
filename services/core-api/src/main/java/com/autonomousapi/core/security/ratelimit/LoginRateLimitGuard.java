package com.autonomousapi.core.security.ratelimit;

import com.autonomousapi.core.error.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protege o login de força bruta. Antes disso não havia limite nenhum: dava para tentar
 * senha à vontade contra qualquer e-mail conhecido.
 *
 * Conta em duas chaves independentes, e basta uma estourar para barrar:
 * - por e-mail, para não deixar martelar uma conta específica de vários IPs;
 * - por IP, para não deixar varrer muitos e-mails do mesmo lugar.
 *
 * A resposta é a mesma (429) tanto para e-mail existente quanto inexistente — assim o
 * limitador não vira um oráculo que revela quais e-mails estão cadastrados.
 */
@Component
public class LoginRateLimitGuard {

    private final RateLimiter rateLimiter;
    private final int maxTentativas;
    private final Duration janela;

    public LoginRateLimitGuard(
            RateLimiter rateLimiter,
            @Value("${app.rate-limit.login-max-attempts}") int maxTentativas,
            @Value("${app.rate-limit.login-window-seconds}") long janelaEmSegundos) {
        this.rateLimiter = rateLimiter;
        this.maxTentativas = maxTentativas;
        this.janela = Duration.ofSeconds(janelaEmSegundos);
    }

    public void verificar(String email, HttpServletRequest request) {
        String porEmail = "rl:login:email:" + email.trim().toLowerCase(Locale.ROOT);
        String porIp = "rl:login:ip:" + clientIp(request);

        boolean emailOk = rateLimiter.tryAcquire(porEmail, maxTentativas, janela);
        boolean ipOk = rateLimiter.tryAcquire(porIp, maxTentativas, janela);

        if (!emailOk || !ipOk) {
            throw new TooManyRequestsException(
                    "Muitas tentativas de login. Aguarde alguns instantes e tente de novo.");
        }
    }

    /**
     * Atrás de proxy/load balancer, getRemoteAddr() devolve o IP do proxy e todo mundo
     * cairia no mesmo balde. X-Forwarded-For é confiável apenas se o proxy da borda o
     * sobrescrever — que é a configuração a garantir no deploy (spec 04).
     */
    private static String clientIp(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
