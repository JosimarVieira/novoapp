package com.novoapp.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.conversation.entity.PendingAction;
import com.novoapp.conversation.entity.PendingResolution;
import com.novoapp.conversation.repository.PendingActionRepository;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abre, encontra e resolve pergunta pendente (ADR-0018).
 *
 * <p>E o mecanismo generico: nao sabe nada sobre categoria, valor ou item de
 * lista -- so guarda o que gerou a pergunta e devolve isso de volta quando a
 * resposta chega. Quem interpreta o conteudo e
 * {@link ConversationOrchestrator}.
 */
@ApplicationScoped
public class PendingActionService {

    @Inject
    PendingActionRepository pendingActions;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Clock clock;

    /**
     * Quanto tempo a pergunta aceita resposta por curto-circuito no chat.
     *
     * <p>Config global, provisoria e explicita, nunca constante escondida:
     * [decisao aberta #8] registra que 10 minutos e sugestao sem base, a
     * calibrar na Etapa 5 com o proprio uso da familia. Passado o prazo a
     * pergunta nao morre -- so muda de superficie (ADR-0018).
     */
    @ConfigProperty(name = "novoapp.conversation.pending-action.ttl", defaultValue = "PT10M")
    Duration timeToLive;

    @Transactional
    @HouseholdScoped
    public PendingAction open(ResolvedContext context, PendingIntent intent,
                              String question, List<String> optionLabels) {
        PendingAction pending = new PendingAction();
        pending.householdId = context.householdId();
        pending.memberId = context.memberId();
        pending.channelIdentityId = context.channelIdentityId();
        pending.intentJson = write(intent);
        pending.questionAsked = question;
        pending.optionsJson = optionLabels == null || optionLabels.isEmpty() ? null : write(optionLabels);
        pending.expiresAt = Instant.now(clock).plus(timeToLive);
        pendingActions.persist(pending);
        // Flush dentro do escopo: no commit o SET LOCAL ROLE ja teria voltado pro
        // papel de fora, que nao tem permissao nesta tabela.
        pendingActions.flush();
        return pending;
    }

    /** A pergunta em aberto desta conversa, expirada ou nao. */
    @Transactional
    @HouseholdScoped
    public Optional<Open> findOpen(ResolvedContext context) {
        return pendingActions.findOpenFor(context.channelIdentityId())
                .map(pending -> new Open(pending.id, read(pending.intentJson), pending.questionAsked,
                        pending.isExpiredAt(Instant.now(clock))));
    }

    /**
     * Troca a pergunta guardada, mantendo a pendencia aberta.
     *
     * <p>Usado quando a resposta da pessoa nao resolve a pendencia mas muda o que
     * falta perguntar -- caso da correcao que pediria dois niveis de hierarquia
     * (ADR-0026 + ADR-0016). Sem isso, a central de pendencias da Etapa 4
     * mostraria a pergunta original, que ja nao e a que esta em aberto.
     */
    @Transactional
    @HouseholdScoped
    public void updateQuestion(UUID pendingActionId, String question) {
        PendingAction pending = pendingActions.findById(pendingActionId);
        if (pending == null || pending.resolvedAt != null) {
            return;
        }
        pending.questionAsked = question;
        pendingActions.flush();
    }

    @Transactional
    @HouseholdScoped
    public void resolve(UUID pendingActionId, PendingResolution resolution) {
        PendingAction pending = pendingActions.findById(pendingActionId);
        if (pending == null || pending.resolvedAt != null) {
            return;
        }
        pending.resolvedAt = Instant.now(clock);
        pending.resolution = resolution;
        pendingActions.flush();
    }

    /** A pendencia aberta, ja desserializada, do jeito que o orquestrador precisa dela. */
    public record Open(UUID id, PendingIntent intent, String questionAsked, boolean expired) {
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel serializar o conteudo da pendencia", e);
        }
    }

    private PendingIntent read(String json) {
        try {
            return objectMapper.readValue(json, PendingIntent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Conteudo de pending_action.intent_json ilegivel: " + json, e);
        }
    }
}
