package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * "comprei o arroz" (ADR-0004, tool <code>marcarItemComprado</code>).
 *
 * <p>Marca item e nada mais: nao cria lancamento nenhum. Fechar a lista gerando
 * despesa e <code>fecharCompra</code>, o elo da Etapa 3 -- o cenario "Marcar
 * item especifico como comprado" exige literalmente que nenhum lancamento
 * financeiro seja criado aqui.
 *
 * <p>O nome do item nao e enum das pendencias: a pessoa pode dizer que comprou
 * algo que ninguem pediu, e esse caso tem cenario proprio (vira pergunta). Os
 * itens pendentes vao no contexto da conversa pro modelo casar a grafia.
 */
public final class MarkItemPurchasedTool {

    public static final String NAME = "marcarItemComprado";
    public static final String ITEM_PARAMETER = "item";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private MarkItemPurchasedTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Marca como comprado um item da lista de compras. Nao registra despesa.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(ITEM_PARAMETER, JsonStringSchema.builder()
                                .description("Nome do produto comprado, no singular e com inicial maiuscula. "
                                        + "Se ele estiver entre os itens pendentes informados no contexto, "
                                        + "use a grafia de la.")
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao.")
                                .build())
                        .required(ITEM_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
