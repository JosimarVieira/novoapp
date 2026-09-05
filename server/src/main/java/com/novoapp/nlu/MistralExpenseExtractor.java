package com.novoapp.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.nlu.spi.ExpenseExtractor;
import com.novoapp.nlu.spi.ExtractedExpense;
import com.novoapp.nlu.tools.RegisterExpenseTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Function calling contra o provedor de LLM (ADR-0004), Mistral na fase de
 * validacao (ADR-0009), sempre por LangChain4j -- nunca chamada direta a API do
 * provedor, pra que a troca na Etapa 5 seja configuracao.
 */
@ApplicationScoped
public class MistralExpenseExtractor implements ExpenseExtractor {

    private static final Logger LOG = Logger.getLogger(MistralExpenseExtractor.class);

    private static final String SYSTEM_PROMPT = """
            Voce interpreta mensagens curtas de chat de uma familia brasileira sobre gastos do dia a dia.
            Mensagens reais sao curtas, sem pontuacao e em qualquer ordem: "mercado 50", "50 mercado",
            "gastei 50 no mercado", "paguei 50 reais de mercado".
            Quando a mensagem descrever uma despesa, chame a ferramenta registrarDespesa.
            Converta o valor para centavos: 50 reais viram 5000.
            Escolha a categoria exatamente entre as opcoes oferecidas. Se nenhuma servir, nao chame
            ferramenta nenhuma -- nunca invente categoria nem valor.""";

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Optional<ExtractedExpense> extract(String text, List<String> categoryNames) {
        if (categoryNames.isEmpty()) {
            // Household sem categoria nenhuma (ADR-0013): o enum da tool ficaria
            // vazio, que nao e schema valido. Nao gasta chamada de modelo.
            return Optional.empty();
        }

        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(text))
                .toolSpecifications(RegisterExpenseTool.specification(categoryNames))
                .build();

        ChatResponse response = chatModel.chat(request);
        List<ToolExecutionRequest> toolCalls = response.aiMessage().toolExecutionRequests();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Optional.empty();
        }

        ToolExecutionRequest call = toolCalls.get(0);
        if (!RegisterExpenseTool.NAME.equals(call.name())) {
            return Optional.empty();
        }
        return parse(call.arguments());
    }

    private Optional<ExtractedExpense> parse(String arguments) {
        try {
            JsonNode json = objectMapper.readTree(arguments);
            JsonNode category = json.get(RegisterExpenseTool.CATEGORY_PARAMETER);
            JsonNode amount = json.get(RegisterExpenseTool.AMOUNT_PARAMETER);
            if (category == null || amount == null || !amount.canConvertToLong()) {
                return Optional.empty();
            }
            JsonNode account = json.get(RegisterExpenseTool.ACCOUNT_PARAMETER);
            return Optional.of(new ExtractedExpense(category.asText(), amount.asLong(),
                    account == null || account.isNull() ? null : account.asText()));
        } catch (Exception e) {
            // Argumento fora do schema e o mesmo que "nao entendi": vira
            // confianca baixa, nunca um lancamento adivinhado.
            LOG.warnf(e, "Argumentos de %s fora do schema: %s", RegisterExpenseTool.NAME, arguments);
            return Optional.empty();
        }
    }
}
