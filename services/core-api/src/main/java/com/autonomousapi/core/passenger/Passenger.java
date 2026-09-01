package com.autonomousapi.core.passenger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cadastro reutilizável de passageiro/cliente final (spec 14) — evita redigitar o mesmo
 *  contato toda vez que ele aparece numa viagem nova (comum em turismo, spec 13). Dado de
 *  terceiro sem consentimento direto (ver spec 14): exclusão é real, não soft-delete — não
 *  existe {@code ativo} aqui de propósito. */
@Entity
@Table(name = "passenger")
public class Passenger {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "telefone", nullable = false, length = 30)
    private String telefone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_link_token", length = 64)
    private String telegramLinkToken;

    protected Passenger() {
        // JPA
    }

    public Passenger(UUID tenantId, String nome, String telefone) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.nome = nome;
        this.telefone = telefone;
        this.createdAt = Instant.now();
        this.telegramLinkToken = UUID.randomUUID().toString();
    }

    public void atualizar(String nome, String telefone) {
        this.nome = nome;
        this.telefone = telefone;
    }

    /** Chamado pelo webhook do Telegram quando o passageiro dá /start no link — a partir
     *  daqui ele passa a receber notificação automática de verdade, não só ter o cadastro. */
    public void vincularTelegram(long chatId) {
        this.telegramChatId = chatId;
    }

    /** Gera um token novo — usado se o gestor quiser reenviar o link (ex. passageiro trocou
     *  de conta do Telegram). Não invalida o vínculo já feito, só o link antigo. */
    public void gerarNovoTokenDeVinculo() {
        this.telegramLinkToken = UUID.randomUUID().toString();
    }

    public boolean temTelegramVinculado() {
        return telegramChatId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public String getTelegramLinkToken() {
        return telegramLinkToken;
    }
}
