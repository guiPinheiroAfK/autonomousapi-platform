package com.autonomousapi.core.billing;

import com.autonomousapi.core.error.SubscriptionRequiredException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica a regra do spec 03: "nunca liberar feature checando estado local, sempre
 * validar subscription.status via core-api". Até esta rodada nada fazia isso — um
 * tenant sem assinatura ativa (nunca assinou, ou está com pagamento atrasado) conseguia
 * escrever no sistema para sempre (ver ADR 0010).
 *
 * Interceptor (não Filter de segurança) de propósito: roda dentro do DispatcherServlet,
 * depois da autenticação JWT já ter populado o SecurityContext, e uma exceção daqui
 * chega ao GlobalExceptionHandler normalmente — um Filter puro exigiria escrever a
 * resposta JSON de erro à mão.
 *
 * Só verbos que mutam (POST/PUT/PATCH/DELETE) passam pela checagem — leitura nunca
 * fica bloqueada, mesmo com assinatura vencida: o tenant não perde acesso ao que já
 * tem, só não consegue adicionar mais nada até resolver o pagamento.
 */
@Component
public class SubscriptionGate implements HandlerInterceptor {

    private final SubscriptionRepository subscriptions;

    public SubscriptionGate(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || !isMutatingMethod(request.getMethod())) {
            return true;
        }

        UUID tenantId = currentTenantId();
        if (tenantId == null) {
            return true; // rota pública/sem principal — quem barra é o Spring Security, não este gate
        }

        boolean liberado = subscriptions.findByTenantId(tenantId)
                .map(Subscription::permiteEscrita)
                .orElse(false);
        if (!liberado) {
            throw new SubscriptionRequiredException(
                    "Trial encerrado ou assinatura inativa — assine para continuar cadastrando.");
        }
        return true;
    }

    private static boolean isMutatingMethod(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private static UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            return null;
        }
        return principal.tenantId();
    }
}
