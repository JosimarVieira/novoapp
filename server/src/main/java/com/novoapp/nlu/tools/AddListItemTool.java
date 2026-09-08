package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * "acabou o arroz" (ADR-0004, tool <code>adicionarItemLista</code>).
 *
 * <p>Recebe uma lista de itens, e nao um item: "acabou arroz, leite e cafe" e
 * uma mensagem so, e o cenario exige um recibo so. Varias chamadas da mesma tool
 * numa resposta seriam varios recibos.
 */
public final class AddListItemTool {

    public static final String NAME = "adicionarItemLista";
    public static final String ITEMS_PARAMETER = "itens";
    public static final String ITEM_NAME_PARAMETER = "nome";
    public static final String ITEM_QUANTITY_PARAMETER = "quantidade";
    public static final String ITEM_UNIT_PARAMETER = "unidade";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private AddListItemTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Adiciona a lista de compras da familia o que a pessoa disse que esta faltando.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(ITEMS_PARAMETER, JsonArraySchema.builder()
                                .description("Um item por coisa mencionada.")
                                .items(JsonObjectSchema.builder()
                                        .addProperty(ITEM_NAME_PARAMETER, JsonStringSchema.builder()
                                                .description("Nome do produto, no singular e com inicial "
                                                        + "maiuscula, sem artigo: 'acabou o arroz' vira "
                                                        + "'Arroz'.")
                                                .build())
                                        .addProperty(ITEM_QUANTITY_PARAMETER, JsonNumberSchema.builder()
                                                .description("Quanto, se a pessoa disse. Vazio no caso comum.")
                                                .build())
                                        .addProperty(ITEM_UNIT_PARAMETER, JsonStringSchema.builder()
                                                .description("Unidade da quantidade: kg, l, un. Vazio se a "
                                                        + "pessoa nao disse.")
                                                .build())
                                        .required(ITEM_NAME_PARAMETER)
                                        .build())
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao.")
                                .build())
                        .required(ITEMS_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
