package com.novoapp.nlu;

import com.novoapp.finance.CategoryView;
import com.novoapp.nlu.spi.InterpretationRequest;
import com.novoapp.nlu.spi.MessageInterpreter;
import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.AddListItemTool;
import com.novoapp.nlu.tools.ConfirmSuggestedCategoryTool;
import com.novoapp.nlu.tools.InviteMemberTool;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import com.novoapp.nlu.tools.QueryListTool;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import com.novoapp.shopping.ItemDraft;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interpretacao: contexto do household + LLM com tools -> {@link Intent} com
 * confianca (sdd-modulo-nlu.md). Nao executa nada, nao persiste dado de
 * dominio, nao pergunta nada -- decidir o que fazer com uma confianca media e
 * trabalho de <code>conversation</code>.
 */
@ApplicationScoped
public class NluService {

    @Inject
    ContextBuilder contextBuilder;

    @Inject
    MessageInterpreter interpreter;

    /**
     * Nome em ingles, e nao <code>interpretar</code> como no SDD: identificador
     * em ingles e regra sem excecao no CLAUDE.md.
     */
    public Intent interpret(UUID householdId, String text) {
        if (text == null || text.isBlank()) {
            return Intent.unknown();
        }

        Map<String, CategoryView> categoriesByLabel = contextBuilder.expenseCategoriesByLabel(householdId);
        InterpretationRequest request = InterpretationRequest.general(text,
                List.copyOf(categoriesByLabel.keySet()), contextBuilder.pendingItemNames(householdId));

        return interpreter.interpret(request)
                .map(call -> toIntent(call, categoriesByLabel))
                .orElseGet(Intent::unknown);
    }

    /**
     * Segunda chamada ao modelo, so pra ler a correcao livre de uma pergunta de
     * criacao de categoria (ADR-0026).
     *
     * <p>E a excecao explicita a regra 6 do CLAUDE.md, e ela vale so aqui: e a
     * unica pendencia cujo terceiro caminho de resolucao nao cabe em
     * curto-circuito deterministico, porque "restaurante dentro de alimentacao"
     * nao e <code>sim</code>, nao e <code>nao</code> e nao e um numero.
     */
    public Intent interpretCategoryCorrection(UUID householdId, String questionAsked, String text) {
        if (text == null || text.isBlank()) {
            return Intent.unknown();
        }

        Map<String, CategoryView> categoriesByLabel = contextBuilder.expenseCategoriesByLabel(householdId);
        InterpretationRequest request = InterpretationRequest.categoryCorrection(text, questionAsked,
                List.copyOf(categoriesByLabel.keySet()));

        return interpreter.interpret(request)
                .filter(call -> ConfirmSuggestedCategoryTool.NAME.equals(call.toolName()))
                .<Intent>map(call -> new Intent.ConfirmSuggestedCategory(
                        call.text(ConfirmSuggestedCategoryTool.NAME_PARAMETER),
                        call.text(ConfirmSuggestedCategoryTool.PARENT_PARAMETER),
                        confidenceOf(call, ConfirmSuggestedCategoryTool.CONFIDENCE_PARAMETER)))
                .filter(intent -> ((Intent.ConfirmSuggestedCategory) intent).name() != null)
                .orElseGet(Intent::unknown);
    }

    private Intent toIntent(ToolCall call, Map<String, CategoryView> categoriesByLabel) {
        return switch (call.toolName()) {
            case RegisterExpenseTool.NAME -> registerExpense(call, categoriesByLabel);
            case AddListItemTool.NAME -> addListItems(call);
            case MarkItemPurchasedTool.NAME -> markItemPurchased(call);
            case QueryListTool.NAME -> new Intent.QueryList(
                    confidenceOf(call, QueryListTool.CONFIDENCE_PARAMETER));
            case InviteMemberTool.NAME -> inviteMember(call);
            default -> Intent.unknown();
        };
    }

    private Intent registerExpense(ToolCall call, Map<String, CategoryView> categoriesByLabel) {
        double confidence = confidenceOf(call, RegisterExpenseTool.CONFIDENCE_PARAMETER);
        Long amountCents = call.integer(RegisterExpenseTool.AMOUNT_PARAMETER);
        String description = call.text(RegisterExpenseTool.DESCRIPTION_PARAMETER);
        String chosen = call.text(RegisterExpenseTool.CATEGORY_PARAMETER);
        String suggested = call.text(RegisterExpenseTool.SUGGESTED_CATEGORY_PARAMETER);

        Optional<Map.Entry<String, CategoryView>> match = chosen == null
                ? Optional.empty()
                : categoriesByLabel.entrySet().stream()
                        .filter(entry -> sameText(entry.getKey(), chosen)
                                || sameText(entry.getValue().name(), chosen))
                        .findFirst();

        if (match.isPresent()) {
            // A regra defensiva que a ADR-0024 deixou explicitamente para a
            // implementacao: se o modelo preencher os dois parametros, a
            // categoria que ja existe vence. Criar categoria nova e o caminho
            // caro e irreversivel dos dois -- na duvida, nao cria.
            CategoryView category = match.get().getValue();
            return new Intent.RegisterExpense(category.id(), category.displayName(),
                    alternativesFor(match.get().getKey(), categoriesByLabel), null,
                    amountCents, description, confidence);
        }

        if (suggested != null) {
            return new Intent.RegisterExpense(null, null, List.of(), suggested,
                    amountCents, description, confidence);
        }

        // Chamou registrarDespesa sem categoria nenhuma: nao da pra registrar e
        // nao da pra oferecer criar o que nao tem nome. Vira confianca baixa,
        // nunca categoria adivinhada.
        return Intent.unknown();
    }

    /**
     * As categorias que tambem poderiam servir, pra virarem opcoes numeradas
     * quando a confianca for media.
     *
     * <p>A escolhida vem primeiro; as demais sao as que compartilham a primeira
     * palavra com ela ("Mercado" e "Mercado livre"). E heuristica sobre os nomes
     * das categorias, nao sobre o texto da pessoa: quem julgou que a mensagem era
     * ambigua foi o modelo, ao devolver confianca media -- aqui so se monta a
     * lista de alternativas plausiveis pra pergunta. Registrado em
     * sdd-modulo-nlu.md.
     */
    private List<Intent.CategoryChoice> alternativesFor(String chosenLabel,
                                                        Map<String, CategoryView> categoriesByLabel) {
        String chosenHead = firstWord(chosenLabel);
        List<Intent.CategoryChoice> choices = new ArrayList<>();
        choices.add(new Intent.CategoryChoice(categoriesByLabel.get(chosenLabel).id(), chosenLabel));
        categoriesByLabel.forEach((label, category) -> {
            if (!label.equals(chosenLabel) && firstWord(label).equals(chosenHead)) {
                choices.add(new Intent.CategoryChoice(category.id(), label));
            }
        });
        return List.copyOf(choices);
    }

    private Intent addListItems(ToolCall call) {
        List<ItemDraft> drafts = new ArrayList<>();
        for (Map<String, Object> item : call.objects(AddListItemTool.ITEMS_PARAMETER)) {
            Object name = item.get(AddListItemTool.ITEM_NAME_PARAMETER);
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            drafts.add(new ItemDraft(String.valueOf(name).trim(),
                    decimal(item.get(AddListItemTool.ITEM_QUANTITY_PARAMETER)),
                    blankToNull(item.get(AddListItemTool.ITEM_UNIT_PARAMETER))));
        }
        if (drafts.isEmpty()) {
            return Intent.unknown();
        }
        return new Intent.AddListItems(drafts, confidenceOf(call, AddListItemTool.CONFIDENCE_PARAMETER));
    }

    private Intent markItemPurchased(ToolCall call) {
        String item = call.text(MarkItemPurchasedTool.ITEM_PARAMETER);
        if (item == null) {
            return Intent.unknown();
        }
        return new Intent.MarkItemPurchased(item,
                confidenceOf(call, MarkItemPurchasedTool.CONFIDENCE_PARAMETER));
    }

    private Intent inviteMember(ToolCall call) {
        String name = call.text(InviteMemberTool.MEMBER_NAME_PARAMETER);
        String phone = call.text(InviteMemberTool.PHONE_PARAMETER);
        if (name == null || phone == null) {
            return Intent.unknown();
        }
        return new Intent.InviteMember(name, phone, confidenceOf(call, InviteMemberTool.CONFIDENCE_PARAMETER));
    }

    /**
     * Confianca ausente ou fora de 0..1 e tratada como zero, nao como um palpite
     * de meio-termo: o modelo que nao respondeu o parametro obrigatorio ja
     * demonstrou que nao seguiu o schema.
     */
    private double confidenceOf(ToolCall call, String parameter) {
        Double confidence = call.number(parameter);
        if (confidence == null || confidence.isNaN() || confidence < 0.0d || confidence > 1.0d) {
            return 0.0d;
        }
        return confidence;
    }

    private String firstWord(String label) {
        String normalized = normalize(label);
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }

    private boolean sameText(String left, String right) {
        return right != null && normalize(left).equals(normalize(right));
    }

    /** Sem acento e em minuscula: "farmacia" e "Farmácia" sao a mesma categoria. */
    private String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private BigDecimal decimal(Object value) {
        return switch (value) {
            case null -> null;
            case Number number -> BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
            default -> {
                try {
                    yield new BigDecimal(String.valueOf(value).trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private String blankToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
