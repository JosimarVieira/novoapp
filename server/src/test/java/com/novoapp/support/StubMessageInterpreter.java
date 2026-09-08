package com.novoapp.support;

import com.novoapp.nlu.spi.InterpretationRequest;
import com.novoapp.nlu.spi.MessageInterpreter;
import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.AddListItemTool;
import com.novoapp.nlu.tools.ConfirmSuggestedCategoryTool;
import com.novoapp.nlu.tools.InviteMemberTool;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import com.novoapp.nlu.tools.QueryListTool;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM stubbado (estrategia-de-testes.md): "o que se testa ali e a politica de
 * confianca e a execucao, nao o modelo".
 *
 * <p><b>Isto nao e, e nunca pode virar, o interpretador de producao.</b> A
 * ADR-0004 descartou explicitamente gramatica fixa: ela cobre "mercado 50" e
 * quebra em "paguei o mercado hoje, uns 50". Aqui a regra burra existe so pra
 * devolver uma extracao previsivel para as mensagens dos `.feature`, sem chamada
 * de rede em teste.
 *
 * <p>Devolve {@link ToolCall} cru -- nome da tool e argumentos -- e nao um
 * resultado ja tipado: e assim que a fronteira {@link MessageInterpreter} e
 * atravessada em producao, e stub que imita uma fronteira mais confortavel do
 * que a real testa menos do que parece.
 *
 * <h2>A confianca tambem e stubbada</h2>
 * Em producao ela vem do modelo, no parametro <code>confianca</code> de cada
 * tool (ADR-0004). Aqui vale a regra determinista: mais de uma categoria do
 * household podendo servir devolve {@link #AMBIGUOUS}, o resto devolve
 * {@link #CERTAIN}. E o que faz o cenario de ambiguidade exercitar a faixa media
 * da politica sem depender de numero de modelo nenhum.
 */
@Mock
@ApplicationScoped
public class StubMessageInterpreter implements MessageInterpreter {

    private static final double CERTAIN = 1.0d;
    private static final double AMBIGUOUS = 0.5d;

    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d,.])(\\d+(?:[.,]\\d{1,2})?)(?![\\d,.])");
    private static final Pattern QUANTITY =
            Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml|un|unidades?|caixas?|pacotes?)?\\s*(?:de\\s+)?(.+)$");
    private static final Pattern PHONE = Pattern.compile("(\\+\\d{8,15})");

    private static final List<String> ADD_ITEM_PREFIXES =
            List.of("acabou o ", "acabou a ", "acabou os ", "acabou as ", "acabou ",
                    "precisa de ", "precisamos de ", "falta o ", "falta a ", "falta ", "comprar ");
    private static final List<String> PURCHASED_PREFIXES =
            List.of("comprei o ", "comprei a ", "comprei os ", "comprei as ", "comprei ");
    private static final List<String> ARTICLES = List.of("o ", "a ", "os ", "as ", "um ", "uma ", "de ");
    /** Nao sobrevivem a extracao da descricao: nao dizem nada sobre o gasto. */
    private static final List<String> STOPWORDS =
            List.of("reais", "real", "r$", "rs", "gastei", "paguei", "no", "na", "em", "de", "do", "da");

    /**
     * As mensagens em que uma regra burra nao consegue imitar o modelo -- e so
     * elas.
     *
     * <p>A regra geral trata tudo que vem antes do valor como o nome da categoria
     * sugerida, e isso resolve "pet shop 80" e "rodizio de pizza 40". Em
     * "restaurante eu e esposa 90" ela produziria a categoria "Restaurante eu e
     * esposa": separar o nome da categoria do residuo que vira descricao e
     * exatamente o julgamento que a ADR-0023 delega ao modelo, e que um stub nao
     * tem como reproduzir. A entrada fixa aqui e o que a estrategia-de-testes.md
     * chama de "Intent fixa", nao um parser escondido.
     */
    private static final Map<String, Map<String, Object>> FIXED_EXPENSES = Map.of(
            "restaurante eu e esposa 90", Map.of(
                    RegisterExpenseTool.SUGGESTED_CATEGORY_PARAMETER, "Restaurante",
                    RegisterExpenseTool.AMOUNT_PARAMETER, 9000L,
                    RegisterExpenseTool.DESCRIPTION_PARAMETER, "eu e esposa",
                    RegisterExpenseTool.CONFIDENCE_PARAMETER, CERTAIN));

    /**
     * Latencia artificial. Chamada de LLM tem cauda imprevisivel (ADR-0005), e o
     * webhook precisa responder 200 em menos de 3s assim mesmo -- o teste de
     * orcamento de resposta usa isto pra provar que a interpretacao ficou de
     * fato fora do ciclo de request.
     */
    private volatile Duration artificialDelay = Duration.ZERO;

    public void delayEachCallBy(Duration delay) {
        this.artificialDelay = delay;
    }

    @Override
    public Optional<ToolCall> interpret(InterpretationRequest request) {
        sleepIfAsked();
        if (request.text() == null || request.text().isBlank()) {
            return Optional.empty();
        }

        String original = request.text().trim();
        String normalized = normalize(original);

        if (request.purpose() == InterpretationRequest.Purpose.CATEGORY_CORRECTION) {
            return categoryCorrection(original, normalized);
        }

        // Ordem importa: "comprei o arroz" tambem casaria com a regra de despesa
        // se ela viesse antes, e "o que esta faltando?" nao tem valor nenhum.
        if (normalized.startsWith("convidar")) {
            return inviteMember(original);
        }
        if (normalized.contains("faltando") || normalized.contains("o que falta")) {
            return Optional.of(new ToolCall(QueryListTool.NAME,
                    Map.of(QueryListTool.CONFIDENCE_PARAMETER, CERTAIN)));
        }
        String purchased = stripPrefix(original, normalized, PURCHASED_PREFIXES);
        if (purchased != null) {
            return Optional.of(new ToolCall(MarkItemPurchasedTool.NAME, Map.of(
                    MarkItemPurchasedTool.ITEM_PARAMETER, titleCase(stripArticle(purchased)),
                    MarkItemPurchasedTool.CONFIDENCE_PARAMETER, CERTAIN)));
        }
        String listed = stripPrefix(original, normalized, ADD_ITEM_PREFIXES);
        if (listed != null) {
            return addListItems(listed);
        }

        return expense(original, normalized, request.expenseCategories());
    }

    // ------------------------------------------------------------------

    private Optional<ToolCall> expense(String original, String normalized, List<String> categoryNames) {
        Map<String, Object> fixed = FIXED_EXPENSES.get(normalized);
        if (fixed != null) {
            return Optional.of(new ToolCall(RegisterExpenseTool.NAME, fixed));
        }

        // Uma categoria "poderia servir" quando a primeira palavra do nome dela
        // aparece na mensagem: e o que faz "Mercado" e "Mercado livre"
        // competirem por "mercado 50", que e o cenario de ambiguidade.
        List<String> candidates = categoryNames.stream()
                .filter(name -> normalized.contains(normalize(firstWord(name))))
                .sorted(Comparator.comparingInt(String::length))
                .toList();

        Matcher amount = AMOUNT.matcher(normalized);
        Long amountCents = amount.find()
                ? new BigDecimal(amount.group(1).replace(',', '.')).movePointRight(2).longValueExact()
                : null;
        String amountText = amountCents == null ? null : amount.group(1);

        Map<String, Object> arguments = new LinkedHashMap<>();
        if (amountCents != null) {
            arguments.put(RegisterExpenseTool.AMOUNT_PARAMETER, amountCents);
        }

        if (!candidates.isEmpty()) {
            String chosen = candidates.get(0);
            arguments.put(RegisterExpenseTool.CATEGORY_PARAMETER, chosen);
            arguments.put(RegisterExpenseTool.CONFIDENCE_PARAMETER,
                    candidates.size() > 1 ? AMBIGUOUS : CERTAIN);
            String description = residue(original, List.of(firstWord(chosen), lastWord(chosen)), amountText);
            if (description != null) {
                arguments.put(RegisterExpenseTool.DESCRIPTION_PARAMETER, description);
            }
            return Optional.of(new ToolCall(RegisterExpenseTool.NAME, arguments));
        }

        if (amountCents == null) {
            // Nem categoria conhecida nem valor: nao da pra chamar tool nenhuma
            // sem inventar as duas coisas.
            return Optional.empty();
        }

        // Nenhuma categoria existente serve: tudo que vem antes do valor vira o
        // nome sugerido (ADR-0024).
        String suggested = titleCase(beforeAmount(original, amountText));
        if (suggested.isBlank()) {
            return Optional.empty();
        }
        arguments.put(RegisterExpenseTool.SUGGESTED_CATEGORY_PARAMETER, suggested);
        arguments.put(RegisterExpenseTool.CONFIDENCE_PARAMETER, CERTAIN);
        return Optional.of(new ToolCall(RegisterExpenseTool.NAME, arguments));
    }

    /**
     * "restaurante dentro de alimentacao" -> nome + categoria-pai. O marcador
     * textual e o mesmo que a propria ADR-0026 usa como exemplo.
     */
    private Optional<ToolCall> categoryCorrection(String original, String normalized) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        int marker = normalized.indexOf(" dentro de ");
        if (marker < 0) {
            arguments.put(ConfirmSuggestedCategoryTool.NAME_PARAMETER, titleCase(original));
        } else {
            arguments.put(ConfirmSuggestedCategoryTool.NAME_PARAMETER,
                    titleCase(original.substring(0, marker).trim()));
            arguments.put(ConfirmSuggestedCategoryTool.PARENT_PARAMETER,
                    titleCase(original.substring(marker + " dentro de ".length()).trim()));
        }
        arguments.put(ConfirmSuggestedCategoryTool.CONFIDENCE_PARAMETER, CERTAIN);
        return Optional.of(new ToolCall(ConfirmSuggestedCategoryTool.NAME, arguments));
    }

    private Optional<ToolCall> addListItems(String listed) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String part : listed.split(",| e ")) {
            String piece = stripArticle(part.trim());
            if (piece.isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            Matcher quantity = QUANTITY.matcher(piece);
            if (quantity.matches()) {
                item.put(AddListItemTool.ITEM_NAME_PARAMETER, titleCase(stripArticle(quantity.group(3).trim())));
                item.put(AddListItemTool.ITEM_QUANTITY_PARAMETER,
                        new BigDecimal(quantity.group(1).replace(',', '.')));
                if (quantity.group(2) != null) {
                    item.put(AddListItemTool.ITEM_UNIT_PARAMETER, quantity.group(2));
                }
            } else {
                item.put(AddListItemTool.ITEM_NAME_PARAMETER, titleCase(piece));
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ToolCall(AddListItemTool.NAME, Map.of(
                AddListItemTool.ITEMS_PARAMETER, items,
                AddListItemTool.CONFIDENCE_PARAMETER, CERTAIN)));
    }

    private Optional<ToolCall> inviteMember(String original) {
        Matcher phone = PHONE.matcher(original);
        if (!phone.find()) {
            return Optional.empty();
        }
        String withoutPhone = original.substring(0, phone.start()).trim();
        String name = withoutPhone.replaceFirst("(?i)^convidar\\s+", "").replaceAll("[,;]\\s*$", "").trim();
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ToolCall(InviteMemberTool.NAME, Map.of(
                InviteMemberTool.MEMBER_NAME_PARAMETER, name,
                InviteMemberTool.PHONE_PARAMETER, phone.group(1),
                InviteMemberTool.CONFIDENCE_PARAMETER, CERTAIN)));
    }

    // ------------------------------------------------------------------

    /**
     * O que sobra da mensagem depois de tirar a categoria e o valor (ADR-0023,
     * "so o residuo"). Vazio vira nulo -- "mercado 50" nao tem descricao.
     */
    private String residue(String original, List<String> categoryWords, String amountText) {
        // Descarta palavra por palavra, comparando sem acento: a pessoa escreve
        // "farmacia" e a categoria se chama "Farmácia". As palavras que sobram
        // saem como estavam na mensagem -- e a descricao, nao uma normalizacao
        // dela.
        List<String> drop = new ArrayList<>(STOPWORDS);
        categoryWords.forEach(word -> drop.add(normalize(word)));
        if (amountText != null) {
            drop.add(normalize(amountText));
        }

        List<String> kept = new ArrayList<>();
        for (String token : original.split("\\s+")) {
            String bare = normalize(token).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
            if (bare.isEmpty() || drop.contains(bare)) {
                continue;
            }
            kept.add(token.replaceAll("^[\\-–—:;,.]+|[\\-–—:;,.]+$", ""));
        }
        return kept.isEmpty() ? null : String.join(" ", kept);
    }

    /**
     * O que acompanha o valor vira o nome da categoria sugerida (ADR-0024).
     *
     * <p>Nao remove preposicao: "rodizio de pizza" e o nome inteiro, e tirar o
     * "de" daria "Rodizio pizza". So caem fora verbo e marca de moeda.
     */
    private String beforeAmount(String original, String amountText) {
        int at = original.indexOf(amountText);
        String around = at <= 0
                ? original.substring(Math.min(original.length(), Math.max(at, 0) + amountText.length()))
                : original.substring(0, at);
        return around.replaceAll("(?iu)\\b(gastei|paguei|reais|real|r\\$)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripPrefix(String original, String normalized, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                return original.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private String stripArticle(String text) {
        String normalized = normalize(text);
        for (String article : ARTICLES) {
            if (normalized.startsWith(article)) {
                return text.substring(article.length()).trim();
            }
        }
        return text.trim();
    }

    /** So a primeira letra: "pet shop" vira "Pet shop", nunca "Pet Shop". */
    private String titleCase(String text) {
        String trimmed = text.trim().replaceAll("[?!.]+$", "").trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private String firstWord(String text) {
        int space = text.trim().indexOf(' ');
        return space < 0 ? text.trim() : text.trim().substring(0, space);
    }

    private String lastWord(String text) {
        int space = text.trim().lastIndexOf(' ');
        return space < 0 ? text.trim() : text.trim().substring(space + 1);
    }

    private void sleepIfAsked() {
        if (artificialDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(artificialDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
