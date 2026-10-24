package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
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
 *
 * <p>Recebe uma lista de itens, como {@link AddListItemTool}, desde 2026-10-24.
 * Ate ali recebia um item so, e em uso real, em 2026-10-20, "Comprei ovos e
 * sal" voltou do modelo como <b>duas</b> chamadas desta tool, uma por item --
 * que era a unica forma de o schema dizer dois itens. O adaptador ficava com a
 * primeira; o sal continuava na lista, e o recibo nao dizia nada sobre ele.
 *
 * <p>A descricao diz que esta ferramenta <b>nao</b> serve para remover. Em uso
 * real, em 2026-09-19, "remover chocolate" voltou como marcarItemComprado com
 * confianca 0,9 -- e o proprio modelo escreveu, na prosa que o sistema
 * descarta, que estava substituindo a intencao por nao ter ferramenta de
 * remover. A pessoa pediu para tirar da lista e o item ficou comprado, que e o
 * status que a Etapa 3 transforma em despesa. Remover item de lista nao existe
 * e esta em DECISOES-ABERTAS; ate existir, "nao entendi" e melhor que o
 * proximo status mais parecido.
 */
public final class MarkItemPurchasedTool {

    public static final String NAME = "marcarItemComprado";
    public static final String ITEMS_PARAMETER = "itens";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private MarkItemPurchasedTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Marca como comprados itens da lista de compras, quando a pessoa diz "
                        + "no passado que ja comprou: 'comprei arroz', 'peguei o arroz', "
                        + "'ja comprei o cafe', 'comprei ovos e sal'. Use tambem quando os itens "
                        + "estiverem entre os itens pendentes do contexto -- e o caso normal. "
                        + "Chame uma vez so, com todos os itens da mensagem na lista. NAO use "
                        + "quando a pessoa pedir para REMOVER, tirar ou apagar o item da lista: "
                        + "quem desistiu de comprar nao comprou -- isso e removerItemLista. NAO use "
                        + "quando houver um valor de dinheiro na mensagem: 'comprei o arroz, 20' e "
                        + "fecharCompra, que marca o item E lanca a despesa. Nao registra despesa.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(ITEMS_PARAMETER, JsonArraySchema.builder()
                                .description("Os produtos que a pessoa disse ter comprado, um por nome "
                                        + "mencionado, no singular e com inicial maiuscula. Se eles "
                                        + "estiverem entre os itens pendentes informados no contexto, "
                                        + "use a grafia de la.")
                                .items(JsonStringSchema.builder().build())
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao. "
                                + "Use valor alto quando a pessoa diz no passado que comprou algo, "
                                + "mesmo que o item nao esteja entre os pendentes do contexto -- "
                                + "esse caso tem tratamento proprio e nao e motivo para baixar a "
                                + "confianca. Use valor baixo so quando nao esta claro se ela "
                                + "comprou ou ainda vai comprar.")
                                .build())
                        .required(ITEMS_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
