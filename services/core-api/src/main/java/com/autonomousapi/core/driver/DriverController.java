package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.DriverRequest;
import com.autonomousapi.core.driver.dto.DriverResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD de motoristas (spec 05, Fase 1). Leitura: qualquer usuário autenticado do tenant. */
@RestController
@RequestMapping("/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverResponse create(@Valid @RequestBody DriverRequest req, Authentication auth) {
        return driverService.create(principal(auth), req);
    }

    @GetMapping
    public List<DriverResponse> list(Authentication auth) {
        return driverService.list(principal(auth));
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable UUID id, Authentication auth) {
        return driverService.get(principal(auth), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverResponse update(
            @PathVariable UUID id, @Valid @RequestBody DriverRequest req, Authentication auth) {
        return driverService.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        driverService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
