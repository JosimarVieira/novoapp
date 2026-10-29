package com.novoapp.conversation.cart.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um item no Carrinho de um membro (glossario, ADR-0040).
 *
 * <p>Mora em <code>conversation</code>, como a {@code PendingAction}: e estado do
 * fio de quem tocou, e nao fato do dominio de mercado. Para a familia o item
 * continua pendente ate o fechamento -- por isso esta linha nao guarda status, e
 * {@link #listItemId} e so o UUID, sem relacao JPA: <code>conversation</code> le
 * o status por <code>shopping</code>, nunca pela tabela dele.
 */
@Entity
@Table(name = "chat_cart_item")
@IdClass(ChatCartItem.Key.class)
public class ChatCartItem extends PanacheEntityBase {

    @Id
    @Column(name = "household_id")
    public UUID householdId;

    @Id
    @Column(name = "member_id")
    public UUID memberId;

    @Id
    @Column(name = "list_item_id")
    public UUID listItemId;

    /** O que o prazo da decisao aberta #28 mede. Nao ha coluna de expiracao. */
    @Column(name = "added_at", nullable = false)
    public Instant addedAt;

    /** A chave primaria composta: um item, uma vez, no carrinho de cada membro. */
    public static class Key implements Serializable {

        public UUID householdId;
        public UUID memberId;
        public UUID listItemId;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(householdId, key.householdId)
                    && Objects.equals(memberId, key.memberId)
                    && Objects.equals(listItemId, key.listItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(householdId, memberId, listItemId);
        }
    }
}
