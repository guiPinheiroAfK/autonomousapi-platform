package com.autonomousapi.core.passenger;

import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.passenger.dto.PassengerRequest;
import com.autonomousapi.core.passenger.dto.PassengerResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD de passageiros/clientes finais reutilizáveis (spec 14). Sem soft-delete (sem campo
 *  {@code ativo}) de propósito — é dado de terceiro sem consentimento direto (spec 14), e
 *  excluir de verdade é a disciplina de retenção certa aqui, não "marcar como inativo". */
@Service
public class PassengerService {

    private final PassengerRepository passengers;

    public PassengerService(PassengerRepository passengers) {
        this.passengers = passengers;
    }

    @Transactional
    public PassengerResponse create(JwtPrincipal gestorPrincipal, PassengerRequest req) {
        Passenger p = passengers.save(new Passenger(gestorPrincipal.tenantId(), req.nome(), req.telefone()));
        return PassengerResponse.from(p);
    }

    @Transactional(readOnly = true)
    public List<PassengerResponse> list(JwtPrincipal gestorPrincipal) {
        return passengers.findAllByTenantIdOrderByNomeAsc(gestorPrincipal.tenantId()).stream()
                .map(PassengerResponse::from)
                .toList();
    }

    @Transactional
    public PassengerResponse update(JwtPrincipal gestorPrincipal, UUID id, PassengerRequest req) {
        Passenger p = find(gestorPrincipal, id);
        p.atualizar(req.nome(), req.telefone());
        return PassengerResponse.from(p);
    }

    /** Exclusão real (não soft-delete) — route_stop.passenger_id é ON DELETE SET NULL
     *  (migration), então rotas passadas continuam íntegras, só perdem o vínculo. */
    @Transactional
    public void delete(JwtPrincipal gestorPrincipal, UUID id) {
        Passenger p = find(gestorPrincipal, id);
        passengers.delete(p);
    }

    private Passenger find(JwtPrincipal gestorPrincipal, UUID id) {
        return Lookups.orNotFound(
                passengers.findByIdAndTenantId(id, gestorPrincipal.tenantId()), "Passageiro não encontrado.");
    }
}
