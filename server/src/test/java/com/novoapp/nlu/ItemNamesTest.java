package com.novoapp.nlu;

import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os nomes que marcarItemComprado e removerItemLista trazem, lidos com a
 * tolerancia que o schema sozinho nao garante: o Mistral nao valida a resposta
 * contra ele.
 */
class ItemNamesTest {

    @Test
    @DisplayName("lista de texto passa como veio")
    void plainList() {
        assertThat(NluService.itemNamesOf(call(List.of("Ovos", "Sal")), MarkItemPurchasedTool.ITEMS_PARAMETER))
                .containsExactly("Ovos", "Sal");
    }

    @Test
    @DisplayName("texto solto, o formato antigo da tool, e uma lista de um")
    void scalarIsOneItem() {
        assertThat(NluService.itemNamesOf(call("Arroz"), MarkItemPurchasedTool.ITEMS_PARAMETER))
                .containsExactly("Arroz");
    }

    /**
     * As tres tools de item chamam o parametro de <code>itens</code>, e em
     * adicionarItemLista cada item e um objeto. Sem isto, o modelo que trocasse
     * o formato produziria o item "{nome=Ovos}" -- e o "sim" da oferta de item
     * fora da lista o gravaria assim.
     */
    @Test
    @DisplayName("objeto no formato de adicionarItemLista vale pelo nome")
    void objectCountsByName() {
        assertThat(NluService.itemNamesOf(call(List.of(Map.of("nome", "Ovos"), Map.of("quantidade", 2))),
                MarkItemPurchasedTool.ITEMS_PARAMETER))
                .containsExactly("Ovos");
    }

    @Test
    @DisplayName("nome repetido, inclusive com outra grafia, conta uma vez, na primeira forma")
    void duplicatesCountOnce() {
        assertThat(NluService.itemNamesOf(call(List.of("Café", "Sal", "cafe", " Sal ")),
                MarkItemPurchasedTool.ITEMS_PARAMETER))
                .containsExactly("Café", "Sal");
    }

    @Test
    @DisplayName("ausente, vazio ou em branco nao vira item")
    void nothingIsNothing() {
        assertThat(NluService.itemNamesOf(new ToolCall(MarkItemPurchasedTool.NAME, Map.of()),
                MarkItemPurchasedTool.ITEMS_PARAMETER)).isEmpty();
        assertThat(NluService.itemNamesOf(call(List.of(" ", "")), MarkItemPurchasedTool.ITEMS_PARAMETER)).isEmpty();
    }

    private static ToolCall call(Object items) {
        return new ToolCall(MarkItemPurchasedTool.NAME, Map.of(
                MarkItemPurchasedTool.ITEMS_PARAMETER, items,
                MarkItemPurchasedTool.CONFIDENCE_PARAMETER, 1));
    }
}
