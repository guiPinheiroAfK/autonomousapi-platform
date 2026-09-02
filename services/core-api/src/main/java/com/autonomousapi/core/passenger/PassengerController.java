package com.autonomousapi.core.passenger;

import com.autonomousapi.core.passenger.dto.PassengerRequest;
import com.autonomousapi.core.passenger.dto.PassengerResponse;
import com.autonomousapi.core.passenger.dto.TelegramLinkResponse;
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

/** Cadastro de passageiro/cliente final reutilizável (spec 14). Leitura aberta aos três
 *  papéis de gestão (spec 15) — Despachante também cria/atribui rota (RoutePlanController)
 *  e precisa listar passageiro pra vincular a uma parada; criar/editar/excluir do cadastro
 *  continua Gestor-only (mesmo padrão de CollectionPointController). */
@RestController
@RequestMapping("/v1/passengers")
public class PassengerController {

    private final PassengerService service;

    public PassengerController(PassengerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public PassengerResponse create(@Valid @RequestBody PassengerRequest req, Authentication auth) {
        return service.create(principal(auth), req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ROTAS_VER')")
    public List<PassengerResponse> list(Authentication auth) {
        return service.list(principal(auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public PassengerResponse update(@PathVariable UUID id, @Valid @RequestBody PassengerRequest req, Authentication auth) {
        return service.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(principal(auth), id);
    }

    /** Link/QR pra vincular o Telegram do passageiro (spec 14) — Despachante também pode
     *  ver, é ele quem mais monta rota e cadastra contato novo na hora. */
    @GetMapping("/{id}/telegram-link")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public TelegramLinkResponse telegramLink(@PathVariable UUID id, Authentication auth) {
        return service.telegramLink(principal(auth), id);
    }

    /** Gera um token novo — usado se o gestor quiser reenviar o link (spec 14). */
    @PostMapping("/{id}/telegram-link")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public TelegramLinkResponse regenerateTelegramLink(@PathVariable UUID id, Authentication auth) {
        return service.regenerateTelegramLink(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
