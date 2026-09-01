package com.autonomousapi.core.team;

import com.autonomousapi.core.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isola a tentativa de hard-delete de {@code app_user} numa transação própria
 * ({@code REQUIRES_NEW}) — usado só por {@link TeamService#remove}.
 *
 * <p>Motivo de existir como classe separada, não um método privado com try/catch dentro do
 * mesmo {@code @Transactional} de {@code remove()}: assim que o Spring traduz a violação de
 * FK do Postgres pra {@code DataIntegrityViolationException}, ele marca a transação atual
 * como rollback-only — capturar a exceção no mesmo método não desfaz essa marca, e o commit
 * no fim do método falha com {@code UnexpectedRollbackException} (achado ao vivo: a resposta
 * HTTP virava 401 "não autenticado" em vez de refletir o erro real). Rodar a tentativa numa
 * transação {@code REQUIRES_NEW} própria isola o dano: ela mesma sofre rollback quando a FK
 * barra o DELETE, mas a transação de fora (a de {@link TeamService#remove}) nunca é tocada —
 * o catch em {@code remove()} pega a exceção depois que essa transação interna já fechou.
 */
@Component
public class UserHardDeleteAttempt {

    private final UserRepository users;

    public UserHardDeleteAttempt(UserRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryDelete(UUID userId) {
        users.deleteById(userId);
        users.flush();
    }
}
