package com.novoapp.finance.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Historico de edicao (glossario, ADR-0012): uma linha por correcao feita num
 * lancamento ja gravado, com quem corrigiu e quando.
 *
 * <p>Append-only. O papel de runtime so tem <code>SELECT</code> e
 * <code>INSERT</code> na tabela (V11): correcao errada se corrige com outra linha,
 * nunca reescrevendo a que ja existe. A unica correcao que o produto faz hoje e a
 * troca de categoria pelo recibo (ADR-0041).
 *
 * <p>{@link #transactionId} e so o UUID, sem relacao JPA, como os outros vinculos
 * de <code>finance</code>: a integridade fica na FK do banco.
 */
@Entity
@Table(name = "transaction_edit")
public class TransactionEdit extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "transaction_id", nullable = false)
    public UUID transactionId;

    @Column(name = "edited_by_member_id", nullable = false)
    public UUID editedByMemberId;

    @Column(name = "edited_at", nullable = false)
    public Instant editedAt;

    /**
     * {@code {"campo": [de, para]}} por campo alterado -- hoje so
     * <code>category_id</code>. Texto, como o <code>intent_json</code> de
     * <code>pending_action</code>; quem garante que o diff e coerente e o servico,
     * nao o banco (ADR-0012, consequencias).
     */
    @Column(name = "changed_fields_json", nullable = false)
    public String changedFieldsJson;
}
