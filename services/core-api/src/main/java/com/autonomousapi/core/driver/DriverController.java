package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.AssignVehicleRequest;
import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.driver.dto.DriverInviteResponse;
import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
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
    private final DriverInviteService inviteService;
    private final DriverAssignmentService assignmentService;

    public DriverController(
            DriverService driverService,
            DriverInviteService inviteService,
            DriverAssignmentService assignmentService) {
        this.driverService = driverService;
        this.inviteService = inviteService;
        this.assignmentService = assignmentService;
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

    /** Motoristas com CNH vencida ou a vencer (alerta, spec 05 Fase 1). */
    @GetMapping("/license-expiring")
    public List<DriverLicenseAlertResponse> licenseExpiring(Authentication auth) {
        return driverService.licenseExpiring(principal(auth));
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

    /** Envia o convite de acesso ao app para o e-mail do motorista (ADR 0013). */
    @PostMapping("/{id}/invite")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverInviteResponse invite(@PathVariable UUID id, Authentication auth) {
        return inviteService.invite(principal(auth), id);
    }

    /** Designa um veículo ao motorista (ADR 0014). */
    @PostMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverAssignmentResponse assign(
            @PathVariable UUID id, @Valid @RequestBody AssignVehicleRequest req, Authentication auth) {
        return assignmentService.assign(principal(auth), id, req.vehicleId());
    }

    /** Encerra a designação ativa do motorista. */
    @PostMapping("/{id}/assignment/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void endAssignment(@PathVariable UUID id, Authentication auth) {
        assignmentService.end(principal(auth), id);
    }

    /** Designação ativa do motorista (null se não houver). Leitura do tenant. */
    @GetMapping("/{id}/assignment")
    public DriverAssignmentResponse activeAssignment(@PathVariable UUID id, Authentication auth) {
        return assignmentService.activeForDriver(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
