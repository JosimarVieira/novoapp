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
 *
 * <p>O nome pede a grafia dos itens pendentes, igual a
 * {@link MarkItemPurchasedTool}. Isso era mitigacao de furo enquanto a
 * comparacao no banco era exata; desde a ADR-0030 o conserto existe -- nome casa
 * pela forma normalizada -- e a frase fica por outro motivo: recibo que devolve
 * "Cafe" onde a familia escreveu "Café" parece erro do bot.
 *
 * <p>A descricao diz o que esta ferramenta <b>nao</b> e, e nao so o que ela e.
 * Em uso real, em 2026-09-18, "comprei cenoura" voltou do Mistral como
 * adicionarItemLista com confianca 0,9 -- o item ja estava pendente, nada mudou,
 * e o recibo foi "ja estava na lista". Confianca alta nao tem rede depois: aqui
 * o prompt e a unica defesa.
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
                .description("Adiciona a lista de compras da familia o que a pessoa disse que esta "
                        + "faltando ou que quer comprar: 'acabou o arroz', 'falta arroz', "
                        + "'comprar arroz', 'adicionar arroz na lista'. NAO use quando a pessoa "
                        + "disser que JA comprou -- 'comprei arroz' e marcarItemComprado.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(ITEMS_PARAMETER, JsonArraySchema.builder()
                                .description("Um item por coisa mencionada.")
                                .items(JsonObjectSchema.builder()
                                        .addProperty(ITEM_NAME_PARAMETER, JsonStringSchema.builder()
                                                .description("Nome do produto, no singular e com inicial "
                                                        + "maiuscula, sem artigo: 'acabou o arroz' vira "
                                                        + "'Arroz'. Se o MESMO produto estiver entre os itens "
                                                        + "pendentes informados no contexto, use a grafia de "
                                                        + "la -- e so a grafia: nunca troque o que a pessoa "
                                                        + "escreveu por outro produto parecido que esteja na "
                                                        + "lista.")
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
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao. "
                                + "Use valor alto quando esta claro que a pessoa quer por algo na "
                                + "lista, mesmo que o produto seja desconhecido, a mensagem esteja "
                                + "mal escrita ou sem verbo: 'colocar chocolate na lista' e "
                                + "'adicionar feijao' sao claras. Use valor baixo so quando voce "
                                + "nao sabe se ela quis a lista ou outra coisa.")
                                .build())
                        .required(ITEMS_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
