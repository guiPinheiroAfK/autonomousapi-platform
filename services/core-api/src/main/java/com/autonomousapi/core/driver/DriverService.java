package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.DriverRequest;
import com.autonomousapi.core.driver.dto.DriverResponse;
import com.autonomousapi.core.error.CnhAlreadyUsedException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mesmo padrão de escopo por tenant do VehicleService: 404 (não 403) fora do tenant. */
@Service
public class DriverService {

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
        drivers.save(driver);
        return DriverResponse.from(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> list(JwtPrincipal principal) {
        return drivers.findAllByTenantIdOrderByCreatedAtDesc(principal.tenantId()).stream()
                .map(DriverResponse::from)
                .toList();
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
        driver.update(req.name(), req.cnh(), req.phone(), req.status());
        return DriverResponse.from(driver);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID id) {
        drivers.delete(findOwned(principal, id));
    }

    private Driver findOwned(JwtPrincipal principal, UUID id) {
        return drivers.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
    }
}
