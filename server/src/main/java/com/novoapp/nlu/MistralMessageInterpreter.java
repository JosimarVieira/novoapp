package com.novoapp.nlu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.nlu.spi.InterpretationRequest;
import com.novoapp.nlu.spi.MessageInterpreter;
import com.novoapp.nlu.spi.ToolCall;
import com.novoapp.nlu.tools.AddListItemTool;
import com.novoapp.nlu.tools.ConfirmSuggestedCategoryTool;
import com.novoapp.nlu.tools.InviteMemberTool;
import com.novoapp.nlu.tools.MarkItemPurchasedTool;
import com.novoapp.nlu.tools.QueryListTool;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Function calling contra o provedor de LLM (ADR-0004), Mistral na fase de
 * validacao (ADR-0009), sempre por LangChain4j -- nunca chamada direta a API do
 * provedor, pra que a troca na Etapa 5 seja configuracao.
 */
@ApplicationScoped
public class MistralMessageInterpreter implements MessageInterpreter {

    private static final Logger LOG = Logger.getLogger(MistralMessageInterpreter.class);

    private static final String GENERAL_PROMPT = """
            Voce interpreta mensagens curtas de chat de uma familia brasileira sobre gastos, lista de
            mercado e convites para a familia.
            Mensagens reais sao curtas, sem pontuacao e em qualquer ordem: "mercado 50", "50 mercado",
            "gastei 50 no mercado", "acabou o arroz", "o que esta faltando?".
            Escolha exatamente uma ferramenta, a que melhor descreve o que a pessoa quis.
            O tempo do verbo separa lista de compra, e errar isso e o erro mais caro aqui:
            - pedido, no futuro ou no infinitivo -- "acabou o arroz", "falta arroz", "precisa de
              arroz", "comprar arroz", "adiciona arroz", "adicionar arroz na lista", "colocar
              arroz na lista", "poe arroz na lista" -- vai para adicionarItemLista;
            - fato, no passado -- "comprei arroz", "ja comprei o arroz", "peguei o arroz",
              "trouxe o arroz" -- vai para marcarItemComprado, mesmo que o item esteja entre os
              itens pendentes do contexto. Estar na lista e justamente o normal nesse caso.
            Valor de dinheiro vai em reais, exatamente como a pessoa escreveu: em "farmacia 32" o
            valor e 32. Nunca multiplique e nunca converta para centavos -- quem faz essa conta e o
            sistema.
            Mensagem sem numero nenhum nao tem valor. "mercado" sozinho e uma categoria sem valor:
            deixe o parametro valor vazio. Repetir um valor de exemplo e inventar dinheiro.
            Responda SEMPRE chamando uma ferramenta. Nunca escreva a chamada como texto na resposta.
            Nunca invente valor, categoria, item nem hierarquia de categoria que a pessoa nao escreveu.
            Preencha sempre o parametro confianca, com honestidade: ele decide se o sistema executa
            direto ou pergunta antes.""";

    /**
     * ADR-0029. O modelo escolhe entre duas leituras, e a segunda so vale com
     * confianca alta: superar a pergunta e destrutivo -- ela sai do fio e o que
     * ja estava capturado deixa de estar ao alcance de um "sim".
     */
    private static final String ANSWERING_PENDING_PROMPT = """
            O bot fez uma pergunta e a pessoa respondeu algo que nao foi sim, nao, desfazer nem um numero.
            Duas leituras sao possiveis, e voce escolhe uma:
            1) a mensagem responde ou corrige a pergunta que o bot fez;
            2) a pessoa ignorou a pergunta e esta pedindo outra coisa.
            Escolha a ferramenta que corresponde a leitura certa.
            So use confianca alta na leitura 2 se estiver claro que a pessoa mudou de assunto. Se a
            mensagem puder ser uma resposta a pergunta, ainda que parcial ou mal escrita, ela e a
            leitura 1 -- responder com o nome de uma das opcoes, em vez do numero dela, e responder.
            Valor de dinheiro vai em reais, exatamente como a pessoa escreveu: em "farmacia 32" o
            valor e 32. Nunca multiplique e nunca converta para centavos -- quem faz essa conta e o
            sistema.
            Mensagem sem numero nenhum nao tem valor. "mercado" sozinho e uma categoria sem valor:
            deixe o parametro valor vazio. Repetir um valor de exemplo e inventar dinheiro.
            Responda SEMPRE chamando uma ferramenta. Nunca escreva a chamada como texto na resposta.
            Nunca invente valor, categoria, item nem hierarquia de categoria que a pessoa nao escreveu.
            Preencha sempre o parametro confianca, com honestidade.""";

    private static final String CATEGORY_CORRECTION_HINT = """
            A pergunta do bot ofereceu criar uma categoria de despesa nova. Se a mensagem for uma
            correcao dessa categoria, chame confirmarCategoriaSugerida com o nome final e, se a pessoa
            indicou uma categoria-pai ("dentro de", "em"), com ela tambem. Nao invente categoria-pai
            que a pessoa nao escreveu.""";

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Optional<ToolCall> interpret(InterpretationRequest request) {
        List<ToolSpecification> tools = toolsFor(request);

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from(systemPromptFor(request)), UserMessage.from(request.text()))
                .toolSpecifications(tools)
                .build());

        List<ToolExecutionRequest> toolCalls = response.aiMessage().toolExecutionRequests();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return recoverFromText(response.aiMessage().text(), tools);
        }

        // Uma tool so, mesmo que o modelo devolva varias: a mensagem e uma acao
        // do usuario, e um recibo. "acabou arroz, leite e cafe" ja e um unico
        // adicionarItemLista com tres itens, nao tres chamadas.
        return parse(toolCalls.get(0));
    }

    /**
     * A correcao de categoria continua sem ser declarada em mensagem comum
     * (ADR-0026, pelo mesmo motivo que a ADR-0024 descartou uma tool
     * <code>criarCategoria</code> geral): ela so entra quando ha, de fato, uma
     * categoria oferecida a corrigir. O que a ADR-0029 mudou e que ela deixou de
     * ser a <b>unica</b> nesse momento -- sozinha, o modelo nao tinha como dizer
     * "isto nao responde a pergunta".
     */
    private List<ToolSpecification> toolsFor(InterpretationRequest request) {
        List<ToolSpecification> tools = new ArrayList<>(List.of(
                RegisterExpenseTool.specification(request.expenseCategories()),
                AddListItemTool.specification(),
                MarkItemPurchasedTool.specification(),
                QueryListTool.specification(),
                InviteMemberTool.specification()));
        if (request.categoryCorrectionOffered()) {
            tools.add(ConfirmSuggestedCategoryTool.specification());
        }
        return List.copyOf(tools);
    }

    /**
     * O contexto real do household vai no prompt de sistema, e nao no schema:
     * categoria e enum porque o modelo precisa ser impedido de inventar uma
     * (ADR-0004), mas item de lista nao -- comprar algo que ninguem pediu e caso
     * legitimo, com cenario proprio.
     */
    private String systemPromptFor(InterpretationRequest request) {
        StringBuilder prompt = new StringBuilder(request.purpose() == InterpretationRequest.Purpose.GENERAL
                ? GENERAL_PROMPT
                : ANSWERING_PENDING_PROMPT);

        if (request.categoryCorrectionOffered()) {
            prompt.append("\n\n").append(CATEGORY_CORRECTION_HINT);
        }
        if (request.questionAsked() != null) {
            prompt.append("\n\nPergunta que o bot fez: ").append(request.questionAsked());
        }
        if (!request.expenseCategories().isEmpty()) {
            prompt.append("\n\nCategorias de despesa que ja existem nesta familia: ")
                    .append(String.join(", ", request.expenseCategories()));
        } else if (request.purpose() == InterpretationRequest.Purpose.GENERAL) {
            prompt.append("\n\nEsta familia ainda nao tem nenhuma categoria de despesa: toda despesa aqui "
                    + "precisa de categoria_sugerida.");
        }
        if (!request.pendingListItems().isEmpty()) {
            prompt.append("\n\nItens que estao faltando na lista de compras: ")
                    .append(String.join(", ", request.pendingListItems()));
        }
        return prompt.toString();
    }

    /**
     * Recupera a chamada quando o modelo a escreve como <b>texto</b> em vez de
     * devolve-la como tool call (observado em producao em 2026-09-18).
     *
     * <p>O ministral-8b faz isso com alguma frequencia: <code>casa 20</code>
     * voltou com <code>finish_reason: stop</code>, <code>tool_calls: null</code>
     * e o conteudo <code>{"categoria_sugerida": "Casa", "valor": 20,
     * "confianca": 0.3}</code> -- ou seja, a interpretacao certa, no formato
     * certo, no campo errado. Descartar isso e mandar "nao entendi" enquanto a
     * resposta esta na mao.
     *
     * <p>Mora no adaptador do provedor, e nao em <code>NluService</code>, porque
     * e defeito de provedor: quem trocar de modelo na Etapa 5 leva o problema
     * (ou nao) junto com o adaptador, e nao com a interpretacao.
     *
     * <p>A tool e deduzida pelos nomes dos parametros, e so quando a deducao e
     * <b>unica</b>: <code>confianca</code> existe em todas, entao um conteudo so
     * com ela nao decide nada e o metodo desiste. Desistir devolve
     * {@link Optional#empty()}, que ja significa "nenhuma tool escolhida" na
     * ADR-0004 -- o comportamento de antes, sem piora.
     */
    static Optional<ToolCall> recoverFromText(String text, List<ToolSpecification> tools) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return Optional.empty();
        }

        Map<String, Object> arguments;
        try {
            arguments = new ObjectMapper().readValue(text.substring(open, close + 1),
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            LOG.debugf("Conteudo sem tool call e sem JSON aproveitavel: %s", text);
            return Optional.empty();
        }
        if (arguments.isEmpty()) {
            return Optional.empty();
        }

        List<ToolSpecification> candidates = tools.stream()
                .filter(tool -> parameterNamesOf(tool).containsAll(arguments.keySet()))
                .toList();
        if (candidates.size() != 1) {
            LOG.debugf("Chamada escrita como texto nao identifica uma tool unica: %s", arguments.keySet());
            return Optional.empty();
        }

        LOG.warnf("Modelo escreveu a chamada de %s como texto; recuperada do conteudo",
                candidates.get(0).name());
        return Optional.of(new ToolCall(candidates.get(0).name(), arguments));
    }

    private static Set<String> parameterNamesOf(ToolSpecification tool) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Set.of();
        }
        return tool.parameters().properties().keySet();
    }

    private Optional<ToolCall> parse(ToolExecutionRequest call) {
        try {
            Map<String, Object> arguments = objectMapper.readValue(call.arguments(),
                    new TypeReference<Map<String, Object>>() { });
            return Optional.of(new ToolCall(call.name(), arguments));
        } catch (Exception e) {
            // Argumento fora do schema e o mesmo que "nao entendi": vira
            // confianca baixa, nunca um lancamento adivinhado.
            LOG.warnf(e, "Argumentos de %s fora do schema: %s", call.name(), call.arguments());
            return Optional.empty();
        }
    }
}
