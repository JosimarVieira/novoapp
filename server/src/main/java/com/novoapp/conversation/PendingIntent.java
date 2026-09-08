package com.novoapp.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * O conteudo de <code>pending_action.intent_json</code>: o que gerou a pergunta,
 * com tudo que a execucao vai precisar quando a resposta chegar.
 *
 * <p>O campo {@code type} e o discriminador -- ver
 * {@link PendingActionType} para o porque de ele viver aqui dentro e nao em
 * coluna propria.
 *
 * <p>Os nomes dos campos sao ingles, diferente do <code>intent_json</code> de
 * <code>inbound_message</code>, que espelha em portugues o vocabulario da tool
 * enviada ao modelo. Nao e inconsistencia: aquele e registro do que foi dito ao
 * LLM, este e estado interno de <code>conversation</code> e nunca sai daqui.
 *
 * @param sourceMessageId a mensagem <b>original</b>, a que gerou a pergunta --
 *        nao a que a respondeu. E o que mantem a rastreabilidade da Etapa 5
 *        apontando pro texto que de fato descreve o lancamento
 * @param options ids e rotulos das alternativas numeradas.
 *        <code>options_json</code> guarda so os rotulos, que e o que a tela da
 *        Etapa 4 mostra; os ids ficam aqui, que e onde a execucao os procura
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingIntent(PendingActionType type,
                            String suggestedCategory,
                            Long amountCents,
                            String description,
                            String itemName,
                            UUID sourceMessageId,
                            List<Option> options) {

    public record Option(UUID id, String label) {
    }

    /** "Criar a categoria 'Pet shop'?" (ADR-0024). */
    public static PendingIntent createCategory(String suggestedCategory, Long amountCents,
                                               String description, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CREATE_CATEGORY, suggestedCategory, amountCents,
                description, null, sourceMessageId, null);
    }

    /** "Foi em qual?" -- confianca media entre categorias parecidas (ADR-0004). */
    public static PendingIntent chooseCategory(List<Option> options, Long amountCents,
                                               String description, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CHOOSE_CATEGORY, null, amountCents,
                description, null, sourceMessageId, options);
    }

    /** "Quanto foi?" -- categoria reconhecida, valor ausente. */
    public static PendingIntent askAmount(List<Option> categoryOption, String description,
                                          UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.ASK_AMOUNT, null, null,
                description, null, sourceMessageId, categoryOption);
    }

    /** "Nao achei 'Feijao' na lista. Registro como comprado?" */
    public static PendingIntent confirmPurchase(String itemName, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_PURCHASE, null, null, null,
                itemName, sourceMessageId, null);
    }

    public List<Option> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }
}
