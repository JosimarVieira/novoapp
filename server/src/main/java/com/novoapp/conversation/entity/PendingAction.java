package com.novoapp.conversation.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma pergunta que o bot fez e ainda espera resposta (glossario, ADR-0018).
 *
 * <p>Mora em <code>conversation</code>, e nao em <code>finance</code> nem em um
 * modulo tecnico proprio: quem pergunta e resolve por chat e
 * <code>conversation</code>, mesmo quando o conteudo da pendencia e financeiro
 * (categoria) ou de mercado (item que nao esta na lista). Os modulos de dominio
 * so recebem o resultado ja resolvido, do mesmo jeito que hoje so recebem
 * {@code Intent} depois de <code>nlu</code> interpretar.
 *
 * <p>{@link #resolution} fica nula enquanto ninguem resolveu, e
 * {@link #expiresAt} no passado nao muda isso sozinho: nenhum job, nenhuma
 * trigger (ADR-0018 descartou explicitamente essa alternativa). O que o prazo
 * muda e so o caminho de resolucao -- dentro dele, curto-circuito no chat; fora
 * dele, a central de pendencias do app (Etapa 4).
 */
@Entity
@Table(name = "pending_action")
public class PendingAction extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "member_id", nullable = false)
    public UUID memberId;

    /**
     * De qual conversa veio a pergunta. Chat e sempre 1:1 (ADR-0008), entao a
     * pendencia pertence ao fio de quem foi perguntado -- o
     * <code>desfazer</code> de outro membro nao cancela a pergunta desta pessoa.
     */
    @Column(name = "channel_identity_id", nullable = false)
    public UUID channelIdentityId;

    /**
     * O que gerou a pergunta, com tudo que a execucao vai precisar quando a
     * resposta chegar. O campo <code>type</code> de dentro deste JSON e o
     * discriminador de {@link com.novoapp.conversation.PendingActionType}.
     */
    @Column(name = "intent_json", nullable = false)
    public String intentJson;

    @Column(name = "question_asked", nullable = false)
    public String questionAsked;

    /** Nulo numa pendencia de sim/nao ou de pergunta aberta. */
    @Column(name = "options_json")
    public String optionsJson;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "resolved_at")
    public Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution")
    public PendingResolution resolution;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }
}
