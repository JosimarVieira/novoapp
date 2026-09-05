package com.novoapp.finance.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lancamento: movimento financeiro (glossario). Nunca chamar de "transacao" em
 * texto de dominio -- esse nome fica reservado pra transacao de banco.
 */
@Entity
@Table(name = "transaction")
public class Transaction extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "category_id", nullable = false)
    public UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    public EntryKind kind;

    /** Inteiro, sempre. Ponto flutuante para dinheiro nao entra aqui. */
    @Column(name = "amount_cents", nullable = false)
    public long amountCents;

    @Column(name = "occurred_on", nullable = false)
    public LocalDate occurredOn;

    @Column(name = "description")
    public String description;

    @Column(name = "created_by_member_id", nullable = false)
    public UUID createdByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    public TransactionSource source;

    /**
     * Mensagem que originou o lancamento. E so o UUID, sem relacao JPA, porque
     * <code>finance</code> nao pode importar <code>channel</code> -- a
     * integridade fica na FK do banco (sdd-visao-geral.md).
     */
    @Column(name = "source_message_id")
    public UUID sourceMessageId;

    /** Estorno em vez de delete (ADR-0012). Preenchido so na Etapa 2. */
    @Column(name = "reversed_at")
    public Instant reversedAt;

    @Column(name = "reversal_of_id")
    public UUID reversalOfId;

    @Column(name = "reversed_by_member_id")
    public UUID reversedByMemberId;

    /** Preenchido quando a conta e do tipo CARD (ADR-0011). Sem uso nesta etapa. */
    @Column(name = "invoice_id")
    public UUID invoiceId;

    @Column(name = "installment_number")
    public Integer installmentNumber;

    @Column(name = "installment_count")
    public Integer installmentCount;

    @Column(name = "installment_group_id")
    public UUID installmentGroupId;

    @Column(name = "split_group_id")
    public UUID splitGroupId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
