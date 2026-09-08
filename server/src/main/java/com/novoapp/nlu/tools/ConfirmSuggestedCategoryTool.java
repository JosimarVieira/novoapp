package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * A terceira via de resposta a uma pergunta de criacao de categoria (ADR-0026):
 * a pessoa nao respondeu <code>sim</code>, <code>nao</code> nem
 * <code>desfazer</code> -- corrigiu a estrutura ("restaurante dentro de
 * alimentacao").
 *
 * <p>E tool separada, e nao parametro a mais em {@code registrarDespesa}, porque
 * o contexto da chamada e outro: a pendencia ja aberta, nao uma mensagem de
 * despesa nova. Ela so e declarada ao modelo nesse momento -- declara-la sempre
 * a poria competindo pela escolha em toda mensagem, que e exatamente o custo
 * que a ADR-0024 usou pra descartar uma tool <code>criarCategoria</code> geral.
 *
 * <p>Esta chamada e a excecao explicita a regra 6 do CLAUDE.md ("confirmacoes
 * nao gastam LLM"), aberta pela ADR-0026 e valida so pra este tipo de pendencia.
 */
public final class ConfirmSuggestedCategoryTool {

    public static final String NAME = "confirmarCategoriaSugerida";
    public static final String NAME_PARAMETER = "nome";
    public static final String PARENT_PARAMETER = "categoria_pai";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private ConfirmSuggestedCategoryTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Registra como a pessoa corrigiu a categoria que o bot ofereceu criar.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(NAME_PARAMETER, JsonStringSchema.builder()
                                .description("Nome final da categoria a criar, como a pessoa o escreveu. Em "
                                        + "'restaurante dentro de alimentacao' o nome e 'Restaurante'.")
                                .build())
                        .addProperty(PARENT_PARAMETER, JsonStringSchema.builder()
                                .description("Categoria-pai, se a pessoa indicou uma ('dentro de', 'em', "
                                        + "'debaixo de'). Em 'restaurante dentro de alimentacao' o pai e "
                                        + "'Alimentacao'. Deixe vazio se a pessoa so corrigiu o nome.")
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta leitura da correcao.")
                                .build())
                        .required(NAME_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
