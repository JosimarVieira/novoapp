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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            Converta valor de dinheiro para centavos: 50 reais viram 5000.
            Nunca invente valor, categoria, item nem hierarquia de categoria que a pessoa nao escreveu.
            Preencha sempre o parametro confianca, com honestidade: ele decide se o sistema executa
            direto ou pergunta antes.""";

    private static final String CATEGORY_CORRECTION_PROMPT = """
            A pessoa esta respondendo a uma pergunta do bot que ofereceu criar uma categoria de despesa
            nova, e a resposta dela nao foi um simples sim ou nao -- e uma correcao.
            Leia a correcao e chame confirmarCategoriaSugerida com o nome final da categoria e, se a
            pessoa indicou uma categoria-pai ("dentro de", "em"), com ela tambem.
            Nao invente categoria-pai que a pessoa nao escreveu.""";

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
            return Optional.empty();
        }

        // Uma tool so, mesmo que o modelo devolva varias: a mensagem e uma acao
        // do usuario, e um recibo. "acabou arroz, leite e cafe" ja e um unico
        // adicionarItemLista com tres itens, nao tres chamadas.
        return parse(toolCalls.get(0));
    }

    private List<ToolSpecification> toolsFor(InterpretationRequest request) {
        return switch (request.purpose()) {
            // So esta tool, e so neste momento: declarar a correcao de categoria
            // sempre a poria competindo pela escolha em toda mensagem (ADR-0026,
            // pelo mesmo motivo que a ADR-0024 descartou uma tool criarCategoria).
            case CATEGORY_CORRECTION -> List.of(ConfirmSuggestedCategoryTool.specification());
            case GENERAL -> List.of(
                    RegisterExpenseTool.specification(request.expenseCategories()),
                    AddListItemTool.specification(),
                    MarkItemPurchasedTool.specification(),
                    QueryListTool.specification(),
                    InviteMemberTool.specification());
        };
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
                : CATEGORY_CORRECTION_PROMPT);

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
