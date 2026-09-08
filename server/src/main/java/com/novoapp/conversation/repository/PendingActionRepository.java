package com.novoapp.conversation.repository;

import com.novoapp.conversation.entity.PendingAction;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PendingActionRepository implements PanacheRepositoryBase<PendingAction, UUID> {

    /**
     * A pergunta em aberto desta conversa -- inclusive a que ja passou do prazo.
     *
     * <p>Nao filtra por <code>expires_at</code>: quem venceu o prazo continua em
     * aberto (ADR-0018), e o chat precisa saber disso pra responder "expirou, veja
     * no aplicativo" em vez de fingir que nunca perguntou nada.
     *
     * <p>Filtra por <code>channel_identity_id</code>, e nao por household: a
     * pergunta foi feita num fio 1:1 (ADR-0008) e e nele que ela se resolve. O
     * <code>desfazer</code> de outro membro cai no estorno, que esse sim e do
     * household inteiro (ADR-0025 + ADR-0012).
     *
     * <p>Nao filtra household_id: quem filtra e a policy de RLS (ADR-0003).
     */
    public Optional<PendingAction> findOpenFor(UUID channelIdentityId) {
        return find("channelIdentityId = ?1 and resolvedAt is null order by createdAt desc",
                channelIdentityId).firstResultOptional();
    }
}
