package com.novoapp.nlu;

import com.novoapp.common.text.Normalization;
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
                                .map(call -> toIntent(call, text, categoriesByLabel))
                .orElseGet(Intent::unknown);
    }

    /**
     * A chamada de uma mensagem que chegou com pergunta em aberto no fio
     * (ADR-0029).
     *
     * <p>Antes desta ADR, aqui so <code>confirmarCategoriaSugerida</code> era
     * declarada, e so para pendencia de categoria: o modelo nao tinha como dizer
     * "isto nao responde a pergunta", e mensagem sobre outro assunto podia virar
     * categoria errada levando junto o valor guardado na pendencia. Agora o
     * cardapio e o do dia a dia, mais a correcao quando ha categoria oferecida a
     * corrigir -- e quem decide o que fazer com o resultado e
     * <code>conversation</code>, como sempre.
     *
     * <p>Continua sendo <b>uma</b> chamada por mensagem, nunca duas: o
     * curto-circuito deterministico da regra 6 do CLAUDE.md roda antes e nao
     * chega aqui.
     */
    public Intent interpretAnsweringPending(UUID householdId, String questionAsked, String text,
                                            boolean categoryCorrectionOffered) {
        if (text == null || text.isBlank()) {
            return Intent.unknown();
        }

        Map<String, CategoryView> categoriesByLabel = contextBuilder.expenseCategoriesByLabel(householdId);
        InterpretationRequest request = InterpretationRequest.answeringPending(text, questionAsked,
                categoryCorrectionOffered, List.copyOf(categoriesByLabel.keySet()),
                contextBuilder.pendingItemNames(householdId));

        return interpreter.interpret(request)
                                .map(call -> toIntent(call, text, categoriesByLabel))
                .orElseGet(Intent::unknown);
    }

    private Intent toIntent(ToolCall call, String text, Map<String, CategoryView> categoriesByLabel) {
        return switch (call.toolName()) {
            case RegisterExpenseTool.NAME -> registerExpense(call, text, categoriesByLabel);
            case AddListItemTool.NAME -> addListItems(call);
            case MarkItemPurchasedTool.NAME -> markItemPurchased(call);
            case QueryListTool.NAME -> new Intent.QueryList(
                    confidenceOf(call, QueryListTool.CONFIDENCE_PARAMETER));
            case InviteMemberTool.NAME -> inviteMember(call);
            case ConfirmSuggestedCategoryTool.NAME -> confirmSuggestedCategory(call);
            default -> Intent.unknown();
        };
    }

    /**
     * So aparece respondendo a uma pendencia de categoria -- a tool nem e
     * declarada fora dela. Sem nome nao ha o que criar, e vira confianca baixa
     * em vez de categoria adivinhada, pelo mesmo criterio de
     * {@link #registerExpense}.
     */
    private Intent confirmSuggestedCategory(ToolCall call) {
        String name = call.text(ConfirmSuggestedCategoryTool.NAME_PARAMETER);
        if (name == null) {
            return Intent.unknown();
        }
        return new Intent.ConfirmSuggestedCategory(name,
                call.text(ConfirmSuggestedCategoryTool.PARENT_PARAMETER),
                confidenceOf(call, ConfirmSuggestedCategoryTool.CONFIDENCE_PARAMETER));
    }

    private Intent registerExpense(ToolCall call, String text,
                                   Map<String, CategoryView> categoriesByLabel) {
        double confidence = confidenceOf(call, RegisterExpenseTool.CONFIDENCE_PARAMETER);
        Long amountCents = amountWrittenBy(call, text);
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

        // O modelo escreveu no campo errado: pos um nome que nao esta no enum em
        // `categoria`, e deixou `categoria_sugerida` vazio. E a contrapartida da
        // regra defensiva logo acima -- la, com os dois preenchidos, a que existe
        // vence; aqui, com so o errado preenchido, o nome vale como sugestao.
        //
        // Acrescentado em 2026-09-18, com dado de uso real: "Madeireira 300"
        // caia neste ponto e virava "nao entendi essa", enquanto "Pet shop 80"
        // -- mensagem da mesma forma -- funcionava, porque naquela o modelo
        // acertou o campo. Descartar a mensagem inteira por erro de campo e o
        // pior desfecho disponivel: o nome esta ali, e criar categoria ja exige
        // confirmacao (ADR-0024), entao nada e criado por engano.
        if (chosen != null) {
            return new Intent.RegisterExpense(null, null, List.of(), chosen,
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
     * O valor em centavos, a partir do valor em reais que o modelo devolveu.
     *
     * <p>A multiplicacao por cem e feita <b>aqui</b>, e nao pelo modelo (mudado
     * em 2026-09-18, com dado de producao). Pedir a conversao a um modelo de 8B
     * era pedir aritmetica: "Mercado 500 fechar lista" voltou com 500 centavos e
     * "comprei toda lista 500 mercado" com 5000, quando as duas eram R$ 500,00.
     * O recibo mostrava R$ 5,00 e R$ 50,00, e ninguem alem de quem conferisse o
     * numero perceberia -- erro silencioso, em dinheiro.
     *
     * <p>{@code BigDecimal} pela forma textual, nunca por {@code double}: o
     * arredondamento de meio pra cima existe so para o modelo que insiste em
     * devolver mais de duas casas, e centavo nenhum se perde no caminho.
     *
     * @return nulo tambem para valor nao positivo -- vira a pergunta "quanto
     *         foi?", nunca um lancamento de zero
     */
    /**
     * O valor so vale se a pessoa escreveu algum digito (ADR-0034).
     *
     * <p>Em uso real, em 2026-09-19, a mensagem <code>mercado</code> -- uma
     * palavra, nenhum numero -- voltou duas vezes como
     * <code>{"categoria": "Mercado", "valor": 50, "confianca": 0.8}</code> e
     * gravou R$ 50,00. O prompt proibia inventar valor em duas linhas
     * separadas; o modelo inventou assim mesmo, completando o padrao do
     * exemplo <code>"mercado 50"</code> que o proprio prompt repetia.
     *
     * <p>Proibir de novo no prompt seria a terceira tentativa da mesma coisa.
     * Esta checagem nao depende do modelo: sem digito na mensagem, nao ha valor
     * a extrair, e o que vier e alucinacao. O desfecho passa a ser a pergunta da
     * ADR-0033 -- "Quanto foi em Mercado?" --, que e o certo.
     */
    private Long amountWrittenBy(ToolCall call, String text) {
        if (text == null || text.chars().noneMatch(Character::isDigit)) {
            return null;
        }
        return amountCentsOf(call);
    }

    private Long amountCentsOf(ToolCall call) {
        BigDecimal amount = call.decimal(RegisterExpenseTool.AMOUNT_PARAMETER);
        if (amount == null) {
            return null;
        }
        long cents = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return cents <= 0 ? null : cents;
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

    /** Sem acento e em minuscula: "farmacia" e "Farmácia" sao a mesma categoria (ADR-0030). */
    private String normalize(String text) {
        return Normalization.of(text);
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
