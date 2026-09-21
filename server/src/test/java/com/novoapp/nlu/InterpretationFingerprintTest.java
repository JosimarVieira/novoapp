package com.novoapp.nlu;

import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A impressao digital do prompt (ADR-0035).
 *
 * <p>Ela e a chave de agrupamento das tres metricas da Etapa 5: a taxa e
 * calculada <b>por</b> <code>prompt_version</code>, e uma correcao de prompt
 * abre uma janela nova em vez de contaminar a anterior. Isso so funciona
 * enquanto o valor for estavel para o mesmo material -- um hash que mudasse a
 * cada boot faria cada mensagem cair no proprio grupo, e nao haveria o que
 * agrupar.
 *
 * <p>Por isso os dois testes abaixo, e nao um so: o primeiro trava a
 * estabilidade; o segundo trava a leitura por reflexao da descricao de cada
 * parametro, que e a parte fragil do calculo e a que mais importa. As duas
 * ultimas mudancas de comportamento observadas em producao foram edicoes de
 * descricao de parametro -- se a reflexao quebrar num upgrade do LangChain4j,
 * o hash degrada em silencio e passa a ignorar exatamente o tipo de mudanca
 * que ele existe para detectar.
 */
class InterpretationFingerprintTest {

    @Test
    @DisplayName("a impressao digital e estavel e tem formato de hash curto")
    void isStable() {
        String first = MistralMessageInterpreter.staticFingerprint();
        String second = MistralMessageInterpreter.staticFingerprint();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(12).matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("a descricao de um parametro entra no material, e nao o nome do tipo")
    void readsParameterDescriptions() {
        JsonStringSchema schema = JsonStringSchema.builder()
                .description("Nome de categoria nova")
                .build();

        assertThat(MistralMessageInterpreter.descriptionOf(schema))
                .as("se cair no fallback do nome do tipo, o hash deixa de enxergar mudanca de prompt")
                .isEqualTo("Nome de categoria nova");
    }
}
