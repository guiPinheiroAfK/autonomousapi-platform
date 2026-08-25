package com.autonomousapi.core.driver;

import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import org.springframework.stereotype.Component;

/**
 * Traduz o token MOTORISTA (JwtPrincipal) no {@link Driver} correspondente (ADR 0013).
 * Base de segurança de todo o app do motorista: nenhum endpoint de {@code /v1/me/*} aceita
 * driverId vindo do cliente — a identidade vem exclusivamente daqui.
 */
@Component
public class CurrentDriverResolver {

    private final DriverRepository drivers;

    public CurrentDriverResolver(DriverRepository drivers) {
        this.drivers = drivers;
    }

    public Driver resolve(JwtPrincipal principal) {
        return Lookups.orNotFound(
                drivers.findByAppUserId(principal.userId()).filter(d -> d.getTenantId().equals(principal.tenantId())),
                "Motorista não encontrado para este login.");
    }
}
