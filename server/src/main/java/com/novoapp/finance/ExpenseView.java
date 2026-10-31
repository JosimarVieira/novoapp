package com.novoapp.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O lancamento como o recibo o mostra (ADR-0041): a leitura de que os botoes do
 * recibo precisam para redesenhar a mensagem a partir do banco -- a mensagem
 * nunca e fonte de verdade.
 *
 * <p>Record, e nao a entidade, pelo mesmo motivo de {@link CategoryView}:
 * <code>conversation</code> le o lancamento por aqui, e um record fechado torna
 * impossivel escrever nele por acidente.
 *
 * @param categoryDisplayName no formato do recibo, com o pai quando ha (ADR-0026)
 * @param description o residuo da mensagem (ADR-0023); nula no caso comum
 * @param occurredOn a data do lancamento, a que a confirmacao de fora da janela
 *        mostra
 * @param createdAt quando o lancamento foi gravado. E dai que a janela do
 *        desfazer pelo botao conta (decisao aberta #30), e nao da data do gasto
 * @param reversed estornado por qualquer caminho: botao, desfazer digitado ou,
 *        da Etapa 4 em diante, a web
 */
public record ExpenseView(UUID transactionId,
                          long amountCents,
                          UUID categoryId,
                          String categoryDisplayName,
                          String description,
                          LocalDate occurredOn,
                          Instant createdAt,
                          UUID createdByMemberId,
                          boolean reversed) {
}
