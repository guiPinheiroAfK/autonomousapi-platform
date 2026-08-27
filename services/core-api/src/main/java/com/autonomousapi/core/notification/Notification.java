package com.autonomousapi.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificação in-app de um usuário (sino do topbar). Vive ao lado do push (ADR 0016),
 * não no lugar dele: {@link com.autonomousapi.core.push.PushNotificationService} entrega
 * no aparelho, esta tabela entrega na tela — o mesmo evento dispara os dois.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private NotificationType tipo;

    @Column(name = "titulo", nullable = false, length = 200, updatable = false)
    private String titulo;

    @Column(name = "corpo", nullable = false, updatable = false)
    private String corpo;

    @Column(name = "link", length = 300, updatable = false)
    private String link;

    @Column(name = "lida", nullable = false)
    private boolean lida;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID userId, NotificationType tipo, String titulo, String corpo, String link) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.corpo = corpo;
        this.link = link;
        this.lida = false;
        this.createdAt = Instant.now();
    }

    public void marcarLida() {
        this.lida = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCorpo() {
        return corpo;
    }

    public String getLink() {
        return link;
    }

    public boolean isLida() {
        return lida;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
