package com.autonomousapi.core.collectionpoint;

import com.autonomousapi.core.collectionpoint.dto.CollectionPointRequest;
import com.autonomousapi.core.collectionpoint.dto.CollectionPointResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pontos de coleta/entrega reutilizáveis (spec 08 item 5). Leitura aberta aos três papéis
 *  de gestão (spec 15) — inclusive Despachante precisa listar pra montar rota (ele cria
 *  rota, ver RoutePlanController); cadastrar/editar/ativar continua Gestor-only. */
@RestController
@RequestMapping("/v1/collection-points")
public class CollectionPointController {

    private final CollectionPointService service;

    public CollectionPointController(CollectionPointService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public CollectionPointResponse create(@Valid @RequestBody CollectionPointRequest req, Authentication auth) {
        return service.create(principal(auth), req);
    }

    /** {@code all=true} devolve inclusive os inativos (tela de cadastro); por padrão só ativos
     *  (é o que a tela de montar rota consome). */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ROTAS_VER')")
    public List<CollectionPointResponse> list(
            @RequestParam(defaultValue = "false") boolean all, Authentication auth) {
        return all ? service.listAll(principal(auth)) : service.listActive(principal(auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public CollectionPointResponse update(
            @PathVariable UUID id, @Valid @RequestBody CollectionPointRequest req, Authentication auth) {
        return service.update(principal(auth), id, req);
    }

    @PostMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public CollectionPointResponse ativar(@PathVariable UUID id, Authentication auth) {
        return service.setAtivo(principal(auth), id, true);
    }

    @PostMapping("/{id}/desativar")
    @PreAuthorize("hasAuthority('PERM_ROTAS_ESCREVER')")
    public CollectionPointResponse desativar(@PathVariable UUID id, Authentication auth) {
        return service.setAtivo(principal(auth), id, false);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
