package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTeamInviteRequest(
        @NotBlank @Email String email,
        @NotBlank String nome,
        @NotNull Role role,
        /** Sem domínio de e-mail verificado ainda, mandar e-mail que provavelmente não
         *  chega é ruído — {@code false} pula o envio, só gera o link pra copiar. Nulo
         *  (clientes antigos) mantém o comportamento de sempre enviar. */
        Boolean enviarPorEmail) {

    public CreateTeamInviteRequest(String email, String nome, Role role) {
        this(email, nome, role, true);
    }
}
