package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

/**
 * A unica tool declarada na Etapa 1 (sdd-modulo-nlu.md).
 *
 * <p>O nome da tool e dos parametros fica em portugues de proposito: nao sao
 * identificadores Java, sao dado enviado ao modelo -- e a ADR-0004 os nomeia
 * assim. O restante do codigo segue a regra de identificador em ingles.
 *
 * <p>O parametro de categoria e um enum montado com as categorias reais do
 * household, nao texto livre: e o que impede o modelo de inventar categoria que
 * a familia nunca criou (ADR-0004, "categorias reais no contexto reduzem
 * alucinacao").
 */
public final class RegisterExpenseTool {

    public static final String NAME = "registrarDespesa";
    public static final String CATEGORY_PARAMETER = "categoria";
    public static final String AMOUNT_PARAMETER = "valor_cents";
    public static final String ACCOUNT_PARAMETER = "conta";

    private RegisterExpenseTool() {
    }

    public static ToolSpecification specification(List<String> categoryNames) {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Registra uma despesa da familia a partir do que a pessoa escreveu no chat.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(CATEGORY_PARAMETER, JsonEnumSchema.builder()
                                .description("Categoria de despesa. Use exatamente uma das existentes.")
                                .enumValues(categoryNames)
                                .build())
                        .addProperty(AMOUNT_PARAMETER, JsonIntegerSchema.builder()
                                .description("Valor da despesa em centavos. 50 reais viram 5000.")
                                .build())
                        .addProperty(ACCOUNT_PARAMETER, JsonStringSchema.builder()
                                .description("Conta de onde saiu o dinheiro, se a pessoa disser qual.")
                                .build())
                        // conta fica de fora: sem ela o lancamento cai na conta
                        // padrao do membro ou na carteira (ADR-0019).
                        .required(CATEGORY_PARAMETER, AMOUNT_PARAMETER)
                        .build())
                .build();
    }
}
