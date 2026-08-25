package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
import com.autonomousapi.core.driver.dto.DriverRequest;
import com.autonomousapi.core.driver.dto.DriverResponse;
import com.autonomousapi.core.error.CnhAlreadyUsedException;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mesmo padrão de escopo por tenant do VehicleService: 404 (não 403) fora do tenant. */
@Service
public class DriverService {

    /**
     * Alerta dispara com CNH vencida ou a vencer nos próximos 30 dias. Público porque o job
     * diário de push (ADR 0016) reaproveita o mesmo limiar — evita o número mágico duplicado.
     */
    public static final int CNH_DAYS_THRESHOLD = 30;

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    @Transactional
    public DriverResponse create(JwtPrincipal principal, DriverRequest req) {
        UUID tenantId = principal.tenantId();
        if (drivers.existsByTenantIdAndCnh(tenantId, req.cnh())) {
            throw new CnhAlreadyUsedException();
        }
        Driver driver = new Driver(tenantId, req.name(), req.cnh(), req.phone());
        driver.update(req.name(), req.cnh(), req.phone(), req.status(), req.cnhValidade(), req.email());
        drivers.save(driver);
        return DriverResponse.from(driver);
    }

    @Transactional(readOnly = true)
    public Page<DriverResponse> list(JwtPrincipal principal, Pageable pageable) {
        return drivers.findAllByTenantIdOrderByCreatedAtDesc(principal.tenantId(), pageable)
                .map(DriverResponse::from);
    }

    @Transactional(readOnly = true)
    public DriverResponse get(JwtPrincipal principal, UUID id) {
        return DriverResponse.from(findOwned(principal, id));
    }

    @Transactional
    public DriverResponse update(JwtPrincipal principal, UUID id, DriverRequest req) {
        Driver driver = findOwned(principal, id);
        if (drivers.existsByTenantIdAndCnhAndIdNot(principal.tenantId(), req.cnh(), id)) {
            throw new CnhAlreadyUsedException();
        }
        driver.update(req.name(), req.cnh(), req.phone(), req.status(), req.cnhValidade(), req.email());
        return DriverResponse.from(driver);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID id) {
        drivers.delete(findOwned(principal, id));
    }

    /** Motoristas com CNH vencida ou a vencer nos próximos 30 dias. */
    @Transactional(readOnly = true)
    public List<DriverLicenseAlertResponse> licenseExpiring(JwtPrincipal principal) {
        LocalDate today = LocalDate.now();
        return drivers.findAllByTenantIdAndCnhValidadeIsNotNull(principal.tenantId()).stream()
                .map(d -> toAlert(d, today))
                .filter(a -> a.diasRestantes() <= CNH_DAYS_THRESHOLD)
                .sorted(Comparator.comparingLong(DriverLicenseAlertResponse::diasRestantes))
                .toList();
    }

    /**
     * Cross-tenant de propósito: usado só pelo job diário de push (ADR 0016). Só motoristas
     * com login (sem login não há pra onde mandar push) — mesmo limiar do alerta do painel.
     */
    @Transactional(readOnly = true)
    public List<DriverLicenseAlertResponse> licenseExpiringAcrossAllTenants() {
        LocalDate today = LocalDate.now();
        return drivers.findAllByCnhValidadeIsNotNullAndAppUserIdIsNotNull().stream()
                .map(d -> toAlert(d, today))
                .filter(a -> a.diasRestantes() <= CNH_DAYS_THRESHOLD)
                .toList();
    }

    private DriverLicenseAlertResponse toAlert(Driver d, LocalDate today) {
        return new DriverLicenseAlertResponse(
                d.getId(), d.getName(), d.getCnhValidade(), ChronoUnit.DAYS.between(today, d.getCnhValidade()));
    }

    private Driver findOwned(JwtPrincipal principal, UUID id) {
        return Lookups.orNotFound(drivers.findByIdAndTenantId(id, principal.tenantId()), "Motorista não encontrado.");
    }
}
