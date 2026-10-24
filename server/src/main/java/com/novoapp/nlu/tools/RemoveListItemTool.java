package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * "remover chocolate" (ADR-0039, tool <code>removerItemLista</code>).
 *
 * <p>Existe por um defeito observado em producao entre 2026-09-18 e 2026-09-19:
 * sem ferramenta de remover, "remover chocolate" voltava como
 * {@link MarkItemPurchasedTool} com confianca 0,9 -- e o proprio modelo escreveu,
 * na prosa que o sistema descarta, que estava substituindo a intencao por nao
 * ter ferramenta melhor. Quem desistiu de comprar nao comprou, e com o elo da
 * Etapa 3 <code>PURCHASED</code> e o status que vira dinheiro.
 *
 * <p>A mitigacao de 2026-09-19 foi so no prompt -- {@link MarkItemPurchasedTool}
 * passou a dizer que nao serve para remover --, o que trocava a escrita errada
 * por "nao entendi". Esta tool e o destino que faltava.
 *
 * <p>Nao mexe em dinheiro e nao e desfazivel pelo chat (ADR-0039): o caminho de
 * volta e avisar de novo que o item esta faltando.
 *
 * <p>Recebe uma lista desde 2026-10-24, pelo mesmo defeito de
 * {@link MarkItemPurchasedTool}: com um item so no schema, "remover chocolate e
 * nescau" so tinha como chegar em duas chamadas, e so a primeira era executada.
 */
public final class RemoveListItemTool {

    public static final String NAME = "removerItemLista";
    public static final String ITEMS_PARAMETER = "itens";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private RemoveListItemTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Tira da lista de compras itens que a familia desistiu de comprar: "
                        + "'remover chocolate', 'tirar o feijao da lista', 'apagar arroz', "
                        + "'nao precisa mais do cafe', 'remover chocolate e nescau'. Chame uma vez "
                        + "so, com todos os itens da mensagem na lista. NAO use quando a pessoa "
                        + "disser que JA comprou -- isso e marcarItemComprado ou fecharCompra, e o "
                        + "status e outro. Nao registra despesa.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(ITEMS_PARAMETER, JsonArraySchema.builder()
                                .description("Os produtos que saem da lista, um por nome mencionado, no "
                                        + "singular e com inicial maiuscula. Se eles estiverem entre os "
                                        + "itens pendentes informados no contexto, use a grafia de la.")
                                .items(JsonStringSchema.builder().build())
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao. "
                                        + "Use valor alto quando a pessoa pede claramente para tirar, "
                                        + "remover ou apagar algo da lista. Use valor baixo quando nao "
                                        + "esta claro se ela quer remover o item ou marca-lo como "
                                        + "comprado.")
                                .build())
                        .required(ITEMS_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
