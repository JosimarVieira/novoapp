package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

/**
 * <b>O elo</b>: "comprei tudo, 180" (ADR-0031, tool <code>fecharCompra</code>).
 *
 * <p>E a mensagem mais cara de errar que o produto tem -- junta compra e
 * dinheiro numa mensagem so (ADR-0032) -- e a unica que escreve nos dois
 * dominios de uma vez.
 *
 * <p>A diferenca para {@link MarkItemPurchasedTool} e o dinheiro, e so ele:
 * marcar item nao cria lancamento nenhum, fechar cria. Por isso as duas
 * descricoes se citam -- o modelo escolhe entre elas o tempo todo, e com sete
 * ferramentas no cardapio a fronteira precisa estar escrita dos dois lados.
 *
 * <p>Uma categoria que <b>ja existe</b> na familia, e nunca uma sugerida
 * (ADR-0037): fechar lista nao e onde a familia batiza categoria nova. Sem
 * categoria resolvivel a interpretacao vira "nao entendi", e nao um palpite --
 * o mesmo criterio que <code>registrarDespesa</code> sem categoria nenhuma ja
 * segue.
 *
 * <p>Item com valor na mesma mensagem -- "acucar 20" -- e fechamento parcial de
 * um item so (ADR-0038), e nao um caso proprio. Ate 2026-09-19 essa mensagem
 * caia ora em <code>adicionarItemLista</code> perdendo o valor, ora em
 * <code>registrarDespesa</code> perdendo o item; a descricao daqui e de
 * {@link AddListItemTool} sao os dois lados do mesmo conserto.
 */
public final class ClosePurchaseTool {

    public static final String NAME = "fecharCompra";
    public static final String ITEMS_PARAMETER = "itens";
    public static final String CATEGORY_PARAMETER = "categoria";
    public static final String AMOUNT_PARAMETER = "valor";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private ClosePurchaseTool() {
    }

    public static ToolSpecification specification(List<String> categoryNames) {
        JsonObjectSchema.Builder parameters = JsonObjectSchema.builder();

        // Household sem nenhuma categoria de despesa (ADR-0013): a propriedade
        // some do schema, como em registrarDespesa -- enum vazio nao e schema
        // valido. Diferente de la, aqui nao ha caminho alternativo: a ADR-0037
        // decide que o fechamento nao cria categoria, entao a familia sem
        // categoria nenhuma passa pelo caminho da despesa comum primeiro.
        if (!categoryNames.isEmpty()) {
            parameters.addProperty(CATEGORY_PARAMETER, JsonEnumSchema.builder()
                    .description("Categoria de despesa que JA EXISTE nesta familia, onde a compra de "
                            + "mercado entra. Use exatamente uma das opcoes. Se a pessoa nao disse "
                            + "qual, escolha a que a familia usa para compras de mercado. Nunca "
                            + "escreva uma categoria que nao esteja nas opcoes.")
                    .enumValues(categoryNames)
                    .build());
        }

        return ToolSpecification.builder()
                .name(NAME)
                .description("Fecha a compra: marca itens da lista como comprados E registra a despesa, "
                        + "numa operacao so. Use quando a pessoa diz no passado que comprou e informa "
                        + "quanto gastou: 'comprei tudo, 180', 'comprei o arroz e o leite, 60', "
                        + "'fechei a lista, 180', 'acucar 20'. Basta haver um nome de produto e um "
                        + "valor na mesma mensagem. NAO use quando nao houver valor de dinheiro "
                        + "nenhum na mensagem: 'comprei o arroz' sem valor e marcarItemComprado, que "
                        + "nao mexe em dinheiro. NAO use quando a pessoa disser o que esta faltando "
                        + "ou o que quer comprar -- isso e adicionarItemLista.")
                .parameters(parameters
                        .addProperty(ITEMS_PARAMETER, JsonArraySchema.builder()
                                .description("Os produtos que a pessoa disse ter comprado, um por nome "
                                        + "mencionado, no singular e com inicial maiuscula. Se eles "
                                        + "estiverem entre os itens pendentes informados no contexto, use "
                                        + "a grafia de la. Deixe VAZIO quando a pessoa comprou a lista "
                                        + "inteira -- 'comprei tudo', 'fechei a lista', 'comprei tudo da "
                                        + "lista' --, e nunca liste os pendentes do contexto por conta "
                                        + "propria nesse caso.")
                                .items(JsonStringSchema.builder().build())
                                .build())
                        .addProperty(AMOUNT_PARAMETER, JsonNumberSchema.builder()
                                .description("Valor total da compra em reais, exatamente como a pessoa "
                                        + "escreveu. Em 'comprei tudo, 180' o valor e 180. Nao "
                                        + "multiplique, nao converta para centavos, nao arredonde, e "
                                        + "nunca some os precos que voce imagina para os itens. Deixe "
                                        + "vazio se a pessoa nao disse quanto foi -- nunca invente um.")
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao. "
                                        + "Use valor alto quando a pessoa diz no passado que comprou e ha "
                                        + "um valor na mensagem. Use valor baixo quando nao esta claro se "
                                        + "ela ja comprou ou ainda vai comprar, ou se o numero na "
                                        + "mensagem pode ser quantidade em vez de dinheiro.")
                                .build())
                        // valor fica fora de required de proposito, como em
                        // registrarDespesa: "comprei tudo" sem valor tem cenario
                        // proprio e vira UMA pergunta curta. Sem isso o modelo nao
                        // chamaria tool nenhuma e a intencao inteira se perderia.
                        .required(CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
