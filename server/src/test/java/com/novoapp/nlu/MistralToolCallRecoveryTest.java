package com.novoapp.nlu;

import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.AddListItemTool;
import com.novoapp.nlu.tools.InviteMemberTool;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import com.novoapp.nlu.tools.QueryListTool;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A recuperacao da chamada que o modelo escreveu como texto.
 *
 * <p>Nao da pra cobrir isto por cenario de aceitacao: o stub de LLM substitui
 * justamente a classe onde o defeito mora, entao um cenario exercitaria o stub e
 * nao o adaptador. Aqui se testa a regra pura, com as especificacoes de tool
 * reais -- e o caso principal e a resposta literal colhida em producao em
 * 2026-09-18.
 */
class MistralToolCallRecoveryTest {

    private static final List<ToolSpecification> TOOLS = List.of(
            RegisterExpenseTool.specification(List.of("Alimentação", "Mercado", "Restaurante")),
            AddListItemTool.specification(),
            MarkItemPurchasedTool.specification(),
            QueryListTool.specification(),
            InviteMemberTool.specification());

    @Test
    @DisplayName("a resposta que o ministral-8b devolveu para \"casa 20\" e recuperada")
    void recoversTheRealProductionResponse() {
        Optional<ToolCall> recovered = MistralMessageInterpreter.recoverFromText(
                "{\"categoria_sugerida\": \"Casa\", \"valor\": 20, \"confianca\": 0.3}", TOOLS);

        assertThat(recovered).isPresent();
        assertThat(recovered.get().toolName()).isEqualTo(RegisterExpenseTool.NAME);
        assertThat(recovered.get().text(RegisterExpenseTool.SUGGESTED_CATEGORY_PARAMETER)).isEqualTo("Casa");
        assertThat(recovered.get().decimal(RegisterExpenseTool.AMOUNT_PARAMETER)).isEqualByComparingTo("20");
    }

    /**
     * O exemplo era <code>{"item": "Arroz"}</code> -> marcarItemComprado ate
     * 2026-10-24, quando a tool passou a receber <code>itens</code>. Com isso
     * ela tem os mesmos parametros de adicionarItemLista, e um JSON so com
     * <code>itens</code> deixou de identificar uma tool unica -- o que ja era
     * verdade em producao desde a Etapa 3, porque removerItemLista tinha os
     * mesmos parametros de marcarItemComprado. O teste e sobre a prosa em volta,
     * e nao sobre qual tool: o convite tem parametros so dele.
     */
    @Test
    @DisplayName("JSON cercado de conversa ainda e recuperado")
    void recoversJsonSurroundedByProse() {
        Optional<ToolCall> recovered = MistralMessageInterpreter.recoverFromText(
                "Claro! Aqui esta: {\"nome\": \"Bruno\", \"telefone\": \"+5511999999999\", \"confianca\": 1}"
                        + " -- espero ter ajudado.", TOOLS);

        assertThat(recovered).isPresent();
        assertThat(recovered.get().toolName()).isEqualTo(InviteMemberTool.NAME);
    }

    @Test
    @DisplayName("lista de itens escrita como texto nao vira palpite entre adicionar e marcar")
    void itemListWrittenAsTextIsAmbiguous() {
        assertThat(MistralMessageInterpreter.recoverFromText(
                "{\"itens\": [\"Arroz\"], \"confianca\": 1}", TOOLS)).isEmpty();
    }

    /**
     * <code>confianca</code> existe em toda tool (ADR-0004), entao um conteudo
     * so com ela nao identifica nenhuma. Desistir e devolver "nenhuma tool
     * escolhida", que ja e o caminho de confianca baixa da ADR-0004 -- nunca uma
     * tool adivinhada.
     */
    @Test
    @DisplayName("conteudo que serve a mais de uma tool nao vira palpite")
    void ambiguousContentIsNotRecovered() {
        assertThat(MistralMessageInterpreter.recoverFromText("{\"confianca\": 0.9}", TOOLS)).isEmpty();
    }

    @Test
    @DisplayName("parametro que nenhuma tool declara nao vira chamada")
    void unknownParametersAreNotRecovered() {
        assertThat(MistralMessageInterpreter.recoverFromText(
                "{\"tarefa\": \"limpar a casa\", \"dia\": \"sabado\"}", TOOLS)).isEmpty();
    }

    @Test
    @DisplayName("texto sem JSON continua sendo nenhuma tool escolhida")
    void proseWithoutJsonIsNotRecovered() {
        assertThat(MistralMessageInterpreter.recoverFromText(
                "Não entendi o que você quis dizer com \"fechar lista\".", TOOLS)).isEmpty();
        assertThat(MistralMessageInterpreter.recoverFromText(null, TOOLS)).isEmpty();
        assertThat(MistralMessageInterpreter.recoverFromText("   ", TOOLS)).isEmpty();
    }

    @Test
    @DisplayName("JSON quebrado nao derruba a interpretacao")
    void malformedJsonIsNotRecovered() {
        assertThat(MistralMessageInterpreter.recoverFromText("{\"valor\": 20,", TOOLS)).isEmpty();
    }
}
