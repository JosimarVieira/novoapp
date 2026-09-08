package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

/**
 * A tool de despesa (ADR-0004), com o que as ADRs 0023, 0024 e 0026
 * acrescentaram na Etapa 2a.
 *
 * <p>O nome da tool e dos parametros fica em portugues de proposito: nao sao
 * identificadores Java, sao dado enviado ao modelo -- e a ADR-0004 os nomeia
 * assim. O restante do codigo segue a regra de identificador em ingles.
 *
 * <p>O parametro de categoria e um enum montado com as categorias reais do
 * household, nao texto livre: e o que impede o modelo de inventar categoria que
 * a familia nunca criou (ADR-0004). Categoria que ainda nao existe tem caminho
 * proprio, {@link #SUGGESTED_CATEGORY_PARAMETER}, mutuamente exclusivo com ele.
 */
public final class RegisterExpenseTool {

    public static final String NAME = "registrarDespesa";
    public static final String CATEGORY_PARAMETER = "categoria";
    public static final String SUGGESTED_CATEGORY_PARAMETER = "categoria_sugerida";
    public static final String AMOUNT_PARAMETER = "valor_cents";
    public static final String ACCOUNT_PARAMETER = "conta";
    public static final String DESCRIPTION_PARAMETER = "descricao";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private RegisterExpenseTool() {
    }

    public static ToolSpecification specification(List<String> categoryNames) {
        JsonObjectSchema.Builder parameters = JsonObjectSchema.builder();

        // Household sem nenhuma categoria de despesa (ADR-0013): a propriedade
        // some do schema em vez de virar um enum vazio, que nao e schema valido.
        // Sem ela o unico caminho que resta ao modelo e categoria_sugerida, que e
        // como a primeira mensagem de um household novo dispara o fluxo de
        // criacao que a ADR-0013 prometeu e a ADR-0024 destravou.
        if (!categoryNames.isEmpty()) {
            parameters.addProperty(CATEGORY_PARAMETER, JsonEnumSchema.builder()
                    .description("Categoria de despesa que JA EXISTE nesta familia. Use exatamente uma das "
                            + "opcoes. Se nenhuma delas corresponder ao que a pessoa escreveu, deixe este "
                            + "parametro vazio e preencha " + SUGGESTED_CATEGORY_PARAMETER + " no lugar. "
                            + "Nunca preencha os dois.")
                    .enumValues(categoryNames)
                    .build());
        }

        return ToolSpecification.builder()
                .name(NAME)
                .description("Registra uma despesa da familia a partir do que a pessoa escreveu no chat.")
                .parameters(parameters
                        .addProperty(SUGGESTED_CATEGORY_PARAMETER, JsonStringSchema.builder()
                                .description("Nome de categoria nova, so quando nenhuma categoria existente "
                                        + "serve. Escreva apenas o nome da categoria como a pessoa a chamou, "
                                        + "no singular e sem o resto da frase: em \"restaurante eu e esposa "
                                        + "90\" a categoria e \"Restaurante\". Nunca invente uma categoria-pai "
                                        + "nem hierarquia que a pessoa nao escreveu. Nunca preencha junto com "
                                        + CATEGORY_PARAMETER + ".")
                                .build())
                        .addProperty(AMOUNT_PARAMETER, JsonIntegerSchema.builder()
                                .description("Valor da despesa em centavos. 50 reais viram 5000. Deixe vazio "
                                        + "se a pessoa nao disse o valor -- nunca invente um.")
                                .build())
                        .addProperty(ACCOUNT_PARAMETER, JsonStringSchema.builder()
                                .description("Conta de onde saiu o dinheiro, se a pessoa disser qual.")
                                .build())
                        .addProperty(DESCRIPTION_PARAMETER, JsonStringSchema.builder()
                                .description("O que sobra da mensagem depois de categoria, valor, conta e "
                                        + "data -- o \"o que\" que da sentido ao lancamento meses depois. Em "
                                        + "\"60 farmacia - remedio joaquim\" a descricao e \"remedio do "
                                        + "Joaquim\". Se nao sobra nada, deixe vazio: \"mercado 50\" nao tem "
                                        + "descricao, e \"compra de mercado\" seria ruido.")
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao. Use "
                                        + "valor alto quando a mensagem e clara e a categoria e inequivoca; "
                                        + "valor intermediario quando mais de uma categoria existente "
                                        + "poderia servir, ou quando falta o valor; valor baixo quando voce "
                                        + "esta adivinhando.")
                                .build())
                        // valor_cents fica fora de required de proposito: "paguei o
                        // mercado" precisa ser expressavel como despesa sem valor,
                        // senao o modelo nao chama tool nenhuma e o bot perde a
                        // categoria que ele ja tinha reconhecido -- e nao consegue
                        // fazer a pergunta curta pedindo o valor.
                        .required(CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
