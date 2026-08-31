package com.autonomousapi.core.passenger;

import com.autonomousapi.core.passenger.dto.PassengerRequest;
import com.autonomousapi.core.passenger.dto.PassengerResponse;
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

/** Cadastro de passageiro/cliente final reutilizável (spec 14) — gestor-only, mesmo padrão
 *  de autorização de CollectionPointController. */
@RestController
@RequestMapping("/v1/passengers")
@PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
public class PassengerController {

    private final PassengerService service;

    public PassengerController(PassengerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PassengerResponse create(@Valid @RequestBody PassengerRequest req, Authentication auth) {
        return service.create(principal(auth), req);
    }

    @GetMapping
    public List<PassengerResponse> list(Authentication auth) {
        return service.list(principal(auth));
    }

    @PutMapping("/{id}")
    public PassengerResponse update(@PathVariable UUID id, @Valid @RequestBody PassengerRequest req, Authentication auth) {
        return service.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
