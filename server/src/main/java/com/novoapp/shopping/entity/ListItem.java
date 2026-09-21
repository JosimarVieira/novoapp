package com.novoapp.shopping.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Item da lista: o que falta, quem pediu, quem comprou (glossario). */
@Entity
@Table(name = "list_item")
public class ListItem extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "shopping_list_id", nullable = false)
    public UUID shoppingListId;

    /** Ja normalizado por <code>nlu</code>: "acabou o arroz" vira "Arroz". */
    @Column(nullable = false)
    public String name;

    /**
     * {@link #name} minusculo e sem acento (ADR-0030). Sustenta o indice unico
     * parcial de item pendente: sem ela, "acabou cafe" com "Café" ja na lista
     * inseria um segundo item, em silencio.
     */
    @Column(name = "name_normalized", nullable = false)
    public String nameNormalized;

    /**
     * Fracionario de proposito: "meio quilo de queijo" e tao comum quanto "2 kg
     * de arroz". Nao viola a regra de dinheiro inteiro -- quantidade de item nao
     * e dinheiro.
     */
    @Column(name = "quantity")
    public BigDecimal quantity;

    @Column(name = "unit")
    public String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public ListItemStatus status;

    @Column(name = "requested_by_member_id", nullable = false)
    public UUID requestedByMemberId;

    @Column(name = "purchased_by_member_id")
    public UUID purchasedByMemberId;

    @Column(name = "purchased_at")
    public Instant purchasedAt;

    /**
     * Qual fechamento comprou este item (ADR-0032: "o fechamento e a unidade" --
     * o <code>desfazer</code> devolve a {@code PENDING} so o que aquele
     * fechamento fechou, nunca a lista inteira).
     *
     * <p>Nulo em item marcado por <code>marcarItemComprado</code>, que nao passou
     * por fechamento nenhum -- e e exatamente por isso que ele nao e desfazivel
     * pelo chat, limite declarado naquela ADR. A coluna nula e onde esse limite
     * fica visivel no schema, em vez de so na prosa.
     */
    @Column(name = "list_checkout_id")
    public UUID listCheckoutId;

    /**
     * Mensagem que pediu o item. So o UUID, sem relacao JPA, porque
     * <code>shopping</code> nao pode importar <code>channel</code> -- a
     * integridade fica na FK do banco, igual em <code>transaction</code>.
     */
    @Column(name = "source_message_id")
    public UUID sourceMessageId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
