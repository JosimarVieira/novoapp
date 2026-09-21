package com.novoapp.shopping.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * O elo: o ato de marcar itens como comprados e gerar o lancamento
 * correspondente (glossario, ADR-0031).
 *
 * <p>Tabela propria, e nao uma FK em <code>transaction</code>: e o que permite
 * fechar a lista parcialmente mais de uma vez, que e cenario escrito ("Segundo
 * fechamento parcial na mesma lista").
 *
 * <p>{@link #transactionId} e obrigatorio e unico. Obrigatorio porque fechamento
 * sem lancamento nao existe -- a ADR-0031 poe as duas escritas na mesma
 * transacao e o cenario de falha exige que nenhuma sobreviva sozinha. Unico
 * porque um lancamento e de no maximo um fechamento: se dois apontassem para a
 * mesma <code>transaction</code>, o <code>desfazer</code> da ADR-0032 nao saberia
 * qual reverter.
 */
@Entity
@Table(name = "list_checkout")
public class ListCheckout extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "shopping_list_id", nullable = false)
    public UUID shoppingListId;

    /**
     * O lancamento que este fechamento gerou. So o UUID, sem relacao JPA:
     * <code>shopping</code> pode depender de <code>finance</code> (o elo e
     * dirigido nesse sentido), mas amarrar as duas entidades faria a fronteira
     * entre os modulos existir so no papel.
     */
    @Column(name = "transaction_id", nullable = false)
    public UUID transactionId;

    @Column(name = "items_purchased_count", nullable = false)
    public int itemsPurchasedCount;

    @Column(name = "performed_by_member_id", nullable = false)
    public UUID performedByMemberId;

    @Column(name = "performed_at", nullable = false)
    public Instant performedAt;
}
