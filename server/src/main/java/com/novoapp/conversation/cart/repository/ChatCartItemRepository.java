package com.novoapp.conversation.cart.repository;

import com.novoapp.conversation.cart.entity.ChatCartItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Nenhuma consulta filtra household_id: quem filtra e a policy de RLS (ADR-0003). */
@ApplicationScoped
public class ChatCartItemRepository implements PanacheRepositoryBase<ChatCartItem, ChatCartItem.Key> {

    /**
     * Separar o item. {@code ON CONFLICT} e o que torna o toque duplo
     * inofensivo (ADR-0040, item 6): o segundo toque nao cria linha.
     *
     * <p>Nao e {@code DO NOTHING} puro, como o modelo de dados escreveu: a linha
     * que ja passou do prazo continuaria la, invisivel, e tocar de novo nao a
     * traria de volta -- o botao pareceria nao funcionar. Ela renasce com o
     * instante do toque. A que esta dentro do prazo fica como esta.
     */
    public void put(UUID householdId, UUID memberId, UUID listItemId, Instant now, Instant freshSince) {
        getEntityManager().createNativeQuery("""
                        INSERT INTO chat_cart_item (household_id, member_id, list_item_id, added_at)
                        VALUES (?1, ?2, ?3, ?4)
                        ON CONFLICT (household_id, member_id, list_item_id)
                        DO UPDATE SET added_at = EXCLUDED.added_at
                        WHERE chat_cart_item.added_at < ?5""")
                .setParameter(1, householdId)
                .setParameter(2, memberId)
                .setParameter(3, listItemId)
                .setParameter(4, now)
                .setParameter(5, freshSince)
                .executeUpdate();
    }

    public void take(UUID memberId, UUID listItemId) {
        delete("memberId = ?1 and listItemId = ?2", memberId, listItemId);
    }

    /** O carrinho dentro do prazo, ainda sem cruzar com o status do item. */
    public List<UUID> listFresh(UUID memberId, Instant freshSince) {
        return find("memberId = ?1 and addedAt >= ?2 order by addedAt", memberId, freshSince).stream()
                .map(item -> item.listItemId)
                .toList();
    }

    /** Tira do carrinho tudo o que nao esta em {@code keep}. */
    public void keepOnly(UUID memberId, Collection<UUID> keep) {
        if (keep.isEmpty()) {
            delete("memberId", memberId);
        } else {
            delete("memberId = ?1 and listItemId not in ?2", memberId, keep);
        }
    }
}
