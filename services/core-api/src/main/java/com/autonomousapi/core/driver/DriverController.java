package com.autonomousapi.core.driver;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.driver.dto.AssignVehicleRequest;
import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.driver.dto.DriverInviteResponse;
import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
import com.autonomousapi.core.driver.dto.DriverRequest;
import com.autonomousapi.core.driver.dto.DriverResponse;
import com.autonomousapi.core.driver.dto.NotifyDriverRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD de motoristas (spec 05, Fase 1). Leitura: qualquer usuário autenticado do tenant. */
@RestController
@RequestMapping("/v1/drivers")
public class DriverController {

    private static final int MAX_PAGE_SIZE = 200;

    private final DriverService driverService;
    private final DriverInviteService inviteService;
    private final DriverAssignmentService assignmentService;
    private final DriverNotificationService notificationService;

    public DriverController(
            DriverService driverService,
            DriverInviteService inviteService,
            DriverAssignmentService assignmentService,
            DriverNotificationService notificationService) {
        this.driverService = driverService;
        this.inviteService = inviteService;
        this.assignmentService = assignmentService;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverResponse create(@Valid @RequestBody DriverRequest req, Authentication auth) {
        return driverService.create(principal(auth), req);
    }

    /**
     * Gestor-only. Achado da revisão do plano de rota multi-parada: até então este endpoint
     * não tinha nenhum {@code @PreAuthorize} (nem comentário justificando, ao contrário do
     * {@code VehicleController}) — um token MOTORISTA conseguia listar nome e CNH de todos os
     * motoristas do tenant, o que contraria o spec 07 ("dados de outros motoristas... fora de
     * escopo"). Fechado junto por já estarmos revisando o shell por papel.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public PageResponse<DriverResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(driverService.list(principal(auth), PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    /**
     * Gestor-only. Achado da revisão de spec (2026-08-17): não tinha nenhum @PreAuthorize —
     * um token MOTORISTA conseguia ler nome, CNH, telefone e e-mail de qualquer motorista
     * do tenant pelo id, mesmo com {@link #list} já fechado. O próprio motorista consulta
     * o próprio perfil via {@code GET /v1/me/profile} (MeController), nunca por aqui.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public DriverResponse get(@PathVariable UUID id, Authentication auth) {
        return driverService.get(principal(auth), id);
    }

    /** Motoristas com CNH vencida ou a vencer (alerta, spec 05 Fase 1). Gestor-only — mesmo
     *  motivo do {@link #list}: CNH de outros motoristas não é dado do motorista logado. */
    @GetMapping("/license-expiring")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
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

    /** Aviso do gestor pro motorista, via push (spec 07 item 5, ADR 0016). */
    @PostMapping("/{id}/notify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void notify(
            @PathVariable UUID id, @Valid @RequestBody NotifyDriverRequest req, Authentication auth) {
        notificationService.notify(principal(auth), id, req.title(), req.body());
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
