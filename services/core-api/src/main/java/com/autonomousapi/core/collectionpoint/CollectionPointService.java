package com.autonomousapi.core.collectionpoint;

import com.autonomousapi.core.collectionpoint.dto.CollectionPointRequest;
import com.autonomousapi.core.collectionpoint.dto.CollectionPointResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD de pontos de coleta/entrega reutilizáveis (spec 08 item 5). Geocodificação
 *  acontece no front via Nominatim (mesmo fluxo de RoutesPage/RoutePlansPage) — aqui só
 *  persiste o que chega, sem chamada a serviço externo. */
@Service
public class CollectionPointService {

    private final CollectionPointRepository collectionPoints;

    public CollectionPointService(CollectionPointRepository collectionPoints) {
        this.collectionPoints = collectionPoints;
    }

    @Transactional
    public CollectionPointResponse create(JwtPrincipal gestorPrincipal, CollectionPointRequest req) {
        CollectionPoint p = collectionPoints.save(new CollectionPoint(
                gestorPrincipal.tenantId(), req.nome(), req.endereco(), req.lat(), req.lon(),
                req.janelaInicio(), req.janelaFim()));
        return CollectionPointResponse.from(p);
    }

    /** Só ativos por padrão — é o que a tela de montar rota precisa; a tela de cadastro
     *  pede tudo via {@link #listAll}. */
    @Transactional(readOnly = true)
    public List<CollectionPointResponse> listActive(JwtPrincipal gestorPrincipal) {
        return collectionPoints.findAllByTenantIdAndAtivoTrueOrderByNomeAsc(gestorPrincipal.tenantId()).stream()
                .map(CollectionPointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionPointResponse> listAll(JwtPrincipal gestorPrincipal) {
        return collectionPoints.findAllByTenantIdOrderByNomeAsc(gestorPrincipal.tenantId()).stream()
                .map(CollectionPointResponse::from)
                .toList();
    }

    @Transactional
    public CollectionPointResponse update(JwtPrincipal gestorPrincipal, UUID id, CollectionPointRequest req) {
        CollectionPoint p = find(gestorPrincipal, id);
        p.atualizar(req.nome(), req.endereco(), req.lat(), req.lon(), req.janelaInicio(), req.janelaFim());
        return CollectionPointResponse.from(p);
    }

    @Transactional
    public CollectionPointResponse setAtivo(JwtPrincipal gestorPrincipal, UUID id, boolean ativo) {
        CollectionPoint p = find(gestorPrincipal, id);
        if (ativo) {
            p.ativar();
        } else {
            p.desativar();
        }
        return CollectionPointResponse.from(p);
    }

    private CollectionPoint find(JwtPrincipal gestorPrincipal, UUID id) {
        return collectionPoints.findByIdAndTenantId(id, gestorPrincipal.tenantId())
                .orElseThrow(() -> new NotFoundException("Ponto de coleta não encontrado."));
    }
}
