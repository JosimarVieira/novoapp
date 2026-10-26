package com.novoapp.nlu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.AddListItemTool;
import com.novoapp.nlu.tools.ClosePurchaseTool;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import com.novoapp.nlu.tools.QueryListTool;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import com.novoapp.nlu.tools.RemoveListItemTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Varias chamadas de tool numa resposta so viram uma intencao.
 *
 * <p>Pelo mesmo motivo de {@link MistralToolCallRecoveryTest}, nao da pra cobrir
 * isto por cenario de aceitacao: o stub de LLM substitui justamente a classe
 * onde a juncao mora. O caso principal e a resposta literal do ministral-8b
 * colhida no log de producao em 2026-10-20, para "Comprei ovos e sal".
 */
class MistralToolCallMergeTest {

    private static final List<ToolSpecification> TOOLS = List.of(
            RegisterExpenseTool.specification(List.of("Mercado")),
            AddListItemTool.specification(),
            MarkItemPurchasedTool.specification(),
            ClosePurchaseTool.specification(List.of("Mercado")),
            RemoveListItemTool.specification(),
            QueryListTool.specification());

    @Test
    @DisplayName("as duas chamadas que o ministral-8b devolveu para \"Comprei ovos e sal\" viram uma")
    void mergesTheRealProductionResponse() {
        // Como o modelo mandou em 2026-10-20, no schema de entao: um item por
        // chamada. No schema de hoje o mesmo modelo mandaria listas de um item.
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                mark(List.of("Ovos"), 1),
                mark(List.of("Sal"), 1)), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().toolName()).isEqualTo(MarkItemPurchasedTool.NAME);
        assertThat(merged.get().strings(MarkItemPurchasedTool.ITEMS_PARAMETER)).containsExactly("Ovos", "Sal");
        assertThat(merged.get().number(MarkItemPurchasedTool.CONFIDENCE_PARAMETER)).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("uma chamada so passa intacta")
    void singleCallIsUntouched() {
        ToolCall only = mark(List.of("Arroz", "Leite"), 0.9);

        assertThat(MistralMessageInterpreter.mergeIntoOne(List.of(only), TOOLS)).contains(only);
    }

    @Test
    @DisplayName("nenhuma chamada continua sendo nenhuma tool escolhida")
    void noCallIsEmpty() {
        assertThat(MistralMessageInterpreter.mergeIntoOne(List.of(), TOOLS)).isEmpty();
    }

    /**
     * Juntar nao pode deixar a interpretacao mais segura do que a parte mais
     * duvidosa dela: a confianca e o que decide se o bot executa ou pergunta.
     */
    @Test
    @DisplayName("a confianca da juncao e a menor das chamadas")
    void mergedConfidenceIsTheLowest() {
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                remove(List.of("Chocolate"), 0.9),
                remove(List.of("Nescau"), 0.5)), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().strings(RemoveListItemTool.ITEMS_PARAMETER)).containsExactly("Chocolate", "Nescau");
        assertThat(merged.get().number(RemoveListItemTool.CONFIDENCE_PARAMETER)).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("confianca ausente numa das chamadas puxa a juncao para zero, como em NluService")
    void missingConfidenceCountsAsZero() {
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                mark(List.of("Ovos"), 1),
                new ToolCall(MarkItemPurchasedTool.NAME, Map.of(
                        MarkItemPurchasedTool.ITEMS_PARAMETER, List.of("Sal")))), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().number(MarkItemPurchasedTool.CONFIDENCE_PARAMETER)).isZero();
    }

    @Test
    @DisplayName("itens de adicionarItemLista, que sao objetos, tambem sao somados")
    void mergesObjectLists() {
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                new ToolCall(AddListItemTool.NAME, Map.of(
                        AddListItemTool.ITEMS_PARAMETER, List.of(Map.of(AddListItemTool.ITEM_NAME_PARAMETER, "Arroz")),
                        AddListItemTool.CONFIDENCE_PARAMETER, 1)),
                new ToolCall(AddListItemTool.NAME, Map.of(
                        AddListItemTool.ITEMS_PARAMETER, List.of(Map.of(AddListItemTool.ITEM_NAME_PARAMETER, "Leite")),
                        AddListItemTool.CONFIDENCE_PARAMETER, 1))), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().objects(AddListItemTool.ITEMS_PARAMETER))
                .extracting(item -> item.get(AddListItemTool.ITEM_NAME_PARAMETER))
                .containsExactly("Arroz", "Leite");
    }

    /**
     * O que fazer com "comprei o arroz e acabou o cafe" e decisao em aberto
     * (DECISOES-ABERTAS #27). Ate la vale o comportamento de antes -- a primeira
     * --, so que com aviso no log em vez de silencio.
     */
    @Test
    @DisplayName("chamada de outra tool continua descartada: fica a primeira")
    void differentToolsKeepTheFirst() {
        ToolCall first = mark(List.of("Arroz"), 1);

        Optional<ToolCall> kept = MistralMessageInterpreter.mergeIntoOne(List.of(
                first,
                new ToolCall(AddListItemTool.NAME, Map.of(
                        AddListItemTool.ITEMS_PARAMETER, List.of(Map.of(AddListItemTool.ITEM_NAME_PARAMETER, "Café")),
                        AddListItemTool.CONFIDENCE_PARAMETER, 1))), TOOLS);

        assertThat(kept).contains(first);
    }

    /**
     * Dois fechamentos com valores diferentes nao se somam: o modelo separou o
     * dinheiro, e somar seria inventar um valor que a pessoa nao escreveu
     * (ADR-0034).
     */
    @Test
    @DisplayName("tool com escalar nao e juntada: dois fecharCompra ficam com o primeiro")
    void toolWithScalarsIsNotMerged() {
        ToolCall first = checkout(List.of("Arroz"), "30");

        Optional<ToolCall> kept = MistralMessageInterpreter.mergeIntoOne(List.of(
                first,
                checkout(List.of("Feijão"), "20")), TOOLS);

        assertThat(kept).contains(first);
    }

    @Test
    @DisplayName("as chamadas da mesma tool sao juntadas mesmo com outra tool no meio")
    void sameToolIsMergedAroundAnotherTool() {
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                mark(List.of("Ovos"), 1),
                new ToolCall(QueryListTool.NAME, Map.of(QueryListTool.CONFIDENCE_PARAMETER, 1)),
                mark(List.of("Sal"), 1)), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().toolName()).isEqualTo(MarkItemPurchasedTool.NAME);
        assertThat(merged.get().strings(MarkItemPurchasedTool.ITEMS_PARAMETER)).containsExactly("Ovos", "Sal");
    }

    /**
     * Texto solto onde o schema pede lista: era o formato desta tool ate
     * 2026-10-24, e um modelo que insistir nele numa das chamadas nao pode
     * perder aquele item na juncao.
     */
    @Test
    @DisplayName("itens escrito como texto numa das chamadas entra como um item")
    void scalarItemsAreKept() {
        Optional<ToolCall> merged = MistralMessageInterpreter.mergeIntoOne(List.of(
                mark(List.of("Ovos"), 1),
                new ToolCall(MarkItemPurchasedTool.NAME, Map.of(
                        MarkItemPurchasedTool.ITEMS_PARAMETER, "Sal",
                        MarkItemPurchasedTool.CONFIDENCE_PARAMETER, 1))), TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().strings(MarkItemPurchasedTool.ITEMS_PARAMETER)).containsExactly("Ovos", "Sal");
    }

    // ------------------------------------------------------------------
    // Das chamadas cruas do provedor ate a intencao -- o caminho de interpret()
    // ------------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("duas chamadas cruas do provedor viram uma intencao com os dois itens")
    void rawProviderCallsAreMerged() {
        Optional<ToolCall> merged = MistralMessageInterpreter.fromToolCalls(List.of(
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Ovos\"], \"confianca\": 1}"),
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Sal\"], \"confianca\": 1}")), MAPPER, TOOLS);

        assertThat(merged).isPresent();
        assertThat(merged.get().strings(MarkItemPurchasedTool.ITEMS_PARAMETER)).containsExactly("Ovos", "Sal");
    }

    /**
     * Juntar sem a parte ilegivel perderia um item calado -- o defeito que a
     * juncao existe para consertar. Argumento fora do schema sempre foi "nao
     * entendi", e continua sendo.
     */
    @Test
    @DisplayName("chamada ilegivel da tool escolhida derruba a mensagem para nao entendi")
    void unreadableSameToolCallIsNotUnderstood() {
        assertThat(MistralMessageInterpreter.fromToolCalls(List.of(
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Ovos\"], \"confianca\": 1}"),
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Sal\"")), MAPPER, TOOLS)).isEmpty();
        assertThat(MistralMessageInterpreter.fromToolCalls(List.of(
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Ovos\""),
                request(QueryListTool.NAME, "{\"confianca\": 1}")), MAPPER, TOOLS)).isEmpty();
    }

    @Test
    @DisplayName("chamada ilegivel de outra tool nao muda nada: seria descartada de qualquer jeito")
    void unreadableOtherToolCallIsIgnored() {
        Optional<ToolCall> kept = MistralMessageInterpreter.fromToolCalls(List.of(
                request(MarkItemPurchasedTool.NAME, "{\"itens\": [\"Ovos\"], \"confianca\": 1}"),
                request(QueryListTool.NAME, "{\"confianca\"")), MAPPER, TOOLS);

        assertThat(kept).isPresent();
        assertThat(kept.get().strings(MarkItemPurchasedTool.ITEMS_PARAMETER)).containsExactly("Ovos");
    }

    // ------------------------------------------------------------------

    private static ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder().id(name).name(name).arguments(arguments).build();
    }

    private static ToolCall mark(List<String> items, double confidence) {
        return new ToolCall(MarkItemPurchasedTool.NAME, Map.of(
                MarkItemPurchasedTool.ITEMS_PARAMETER, items,
                MarkItemPurchasedTool.CONFIDENCE_PARAMETER, confidence));
    }

    private static ToolCall remove(List<String> items, double confidence) {
        return new ToolCall(RemoveListItemTool.NAME, Map.of(
                RemoveListItemTool.ITEMS_PARAMETER, items,
                RemoveListItemTool.CONFIDENCE_PARAMETER, confidence));
    }

    private static ToolCall checkout(List<String> items, String amount) {
        return new ToolCall(ClosePurchaseTool.NAME, Map.of(
                ClosePurchaseTool.ITEMS_PARAMETER, items,
                ClosePurchaseTool.CATEGORY_PARAMETER, "Mercado",
                ClosePurchaseTool.AMOUNT_PARAMETER, new BigDecimal(amount),
                ClosePurchaseTool.CONFIDENCE_PARAMETER, 1));
    }
}
