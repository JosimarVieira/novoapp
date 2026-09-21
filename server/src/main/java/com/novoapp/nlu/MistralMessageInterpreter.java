package com.novoapp.nlu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.nlu.spi.InterpretationProvenance;
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
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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

    /**
     * Valor qualquer, fixo, no lugar das categorias reais ao calcular a
     * impressao digital: sem ele a propriedade <code>categoria</code> sumiria do
     * schema (ADR-0024) e a descricao dela ficaria fora do hash. Com ele, o
     * enum entra com um valor constante -- a descricao e versionada, a familia
     * nao.
     */
    private static final String FINGERPRINT_CATEGORY = "_";

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "quarkus.langchain4j.mistralai.chat-model.model-name")
    String modelName;

    /**
     * Tentativas de pipeline, contando a primeira. Duas, e nao tres: o
     * LangChain4j ja tenta tres vezes por conta propria em cerca de 1,7s (ver
     * comentario em <code>application.properties</code>), entao duas aqui ja sao
     * ate seis chamadas ao provedor.
     */
    @ConfigProperty(name = "novoapp.nlu.mistral.retry.attempts", defaultValue = "2")
    int retryAttempts;

    @ConfigProperty(name = "novoapp.nlu.mistral.retry.backoff", defaultValue = "PT2S")
    Duration retryBackoff;

    private volatile InterpretationProvenance provenance;

    @Override
    public Optional<ToolCall> interpret(InterpretationRequest request) {
        List<ToolSpecification> tools = toolsFor(request);

        ChatRequest chat = ChatRequest.builder()
                .messages(SystemMessage.from(systemPromptFor(request)), UserMessage.from(request.text()))
                .toolSpecifications(tools)
                .build();

        ChatResponse response = callWithRetry(() -> chatModel.chat(chat), retryAttempts, retryBackoff);

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

    /**
     * Uma tentativa a mais quando a falha e transitoria, dentro da mesma tarefa.
     *
     * <p>Fecha a lacuna aberta desde a Etapa 1 -- "retry com backoff" estava no
     * desenho e nunca no codigo, e a mensagem morria na primeira falha de rede.
     * O agravante e a idempotencia da ADR-0005: o webhook ja respondeu 200,
     * entao o Telegram nao reenvia, e se reenviasse o guard descartaria. Nao ha
     * segunda chance vinda de fora.
     *
     * <p><b>Nao e job nem agendador.</b> {@code InboundDispatcher} ja roda cada
     * mensagem numa virtual thread propria, fora do ciclo do request; isto e um
     * laco com espera dentro dessa mesma tarefa. Nada do que as ADRs 0014, 0020
     * e 0028 recusaram ("processo rodando no vazio") entra aqui -- aquelas falam
     * de estado derivado, e isto e trabalho inacabado.
     *
     * <p>Mora no adaptador, e nao em {@code NluService}, pelo mesmo motivo de
     * {@link #recoverFromText}: decidir o que vale repetir exige olhar o tipo da
     * excecao do provedor, e nenhum tipo do LangChain4j atravessa a fronteira
     * {@code MessageInterpreter} (ADR-0009). Quem trocar de provedor na Etapa 5
     * leva esta politica junto com o adaptador.
     *
     * <p>Custo declarado: com {@code timeout} de 20s e duas tentativas, o pior
     * caso ate o recibo de erro fica em torno de 42s. Se isso se mostrar longo
     * demais no uso real, o que se mexe e o <b>timeout</b>, nao o numero de
     * tentativas -- e o timeout que domina a conta.
     */
    static ChatResponse callWithRetry(Supplier<ChatResponse> call, int attempts, Duration backoff) {
        int limit = Math.max(1, attempts);
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException failure) {
                if (attempt >= limit || !worthRetrying(failure)) {
                    throw failure;
                }
                LOG.warnf("Tentativa %d de %d falhou (%s); repetindo em %s",
                        attempt, limit, failure.getClass().getSimpleName(), backoff);
                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException interrupted) {
                    // Desligando. Restaura a flag e desiste com a causa real, e
                    // nao com "interrompido": quem le o recibo precisa saber o
                    // que de fato falhou.
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    /**
     * O LangChain4j 1.19 ja classifica a falha, e a classificacao dele e usada
     * como esta -- menos em um ponto.
     *
     * <p>{@code RateLimitException} <b>e</b> uma {@code RetriableException} na
     * biblioteca, e aqui nao e repetida. Insistir num 429 gasta cota para
     * provavelmente tomar outro 429, e o tier gratuito do Mistral e exatamente
     * onde isso doi: a ADR-0009 registra o throttling como negativa conhecida, e
     * o proprio retry interno da biblioteca (tres vezes em 1,7s) ja foi o que
     * obrigou a trocar de modelo por causa de limite de requisicao. O usuario
     * recebe o recibo de erro e reescreve, que custa menos que a cota.
     *
     * <p>Excecao que nao seja {@code RetriableException} -- schema invalido,
     * chave errada, modelo inexistente -- e defeito nosso: repetir so atrasa o
     * aviso. O mesmo vale para qualquer excecao de fora dessa hierarquia, sobre
     * a qual nao se sabe nada.
     */
    static boolean worthRetrying(RuntimeException failure) {
        if (failure instanceof RateLimitException) {
            return false;
        }
        return failure instanceof RetriableException;
    }

    /**
     * A impressao digital da parte <b>estatica</b> do que este adaptador manda
     * ao modelo (ADR-0035): os tres textos de prompt, as seis tools, e o nome e
     * a descricao de cada parametro delas.
     *
     * <p>Fica de fora, de proposito, tudo que muda por mensagem -- categorias e
     * itens do household, pergunta pendente. Aquilo e contexto, nao versao: se
     * entrasse, cada familia teria a propria "versao de prompt" e a metrica da
     * Etapa 5 nao agruparia nada.
     *
     * <p>E hash, e nao uma constante incrementada a mao, porque constante
     * depende de alguem lembrar. As duas ultimas mudancas de comportamento
     * observadas em producao foram edicoes na <i>descricao de um parametro</i>
     * -- <code>confianca</code> e <code>marcarItemComprado</code> -- e e
     * exatamente esse tipo de mudanca que ninguem trata como "versao nova".
     *
     * <p>Calculada uma vez, em campo volatil: o material e constante em tempo de
     * execucao, e dois calculos concorrentes produzem o mesmo valor.
     */
    @Override
    public InterpretationProvenance provenance() {
        InterpretationProvenance cached = provenance;
        if (cached == null) {
            cached = new InterpretationProvenance(staticFingerprint(), modelName);
            provenance = cached;
        }
        return cached;
    }

    static String staticFingerprint() {
        StringBuilder material = new StringBuilder()
                .append(GENERAL_PROMPT).append('\u0000')
                .append(ANSWERING_PENDING_PROMPT).append('\u0000')
                .append(CATEGORY_CORRECTION_HINT).append('\u0000');

        for (ToolSpecification tool : List.of(
                RegisterExpenseTool.specification(List.of(FINGERPRINT_CATEGORY)),
                AddListItemTool.specification(),
                MarkItemPurchasedTool.specification(),
                QueryListTool.specification(),
                InviteMemberTool.specification(),
                ConfirmSuggestedCategoryTool.specification())) {
            material.append(tool.name()).append('\u0000')
                    .append(tool.description()).append('\u0000');
            if (tool.parameters() != null && tool.parameters().properties() != null) {
                tool.parameters().properties().forEach((name, schema) -> material
                        .append(name).append('=')
                        .append(descriptionOf(schema)).append('\u0000'));
            }
        }
        return sha256Hex(material.toString()).substring(0, 12);
    }

    /**
     * Os tipos de schema do LangChain4j nao compartilham um
     * <code>description()</code> numa interface comum, entao a leitura e por
     * reflexao. Falhar aqui degrada para o nome do tipo em vez de quebrar a
     * interpretacao: a impressao digital fica mais fraca, a mensagem do usuario
     * continua sendo respondida.
     */
    static String descriptionOf(Object schema) {
        try {
            return String.valueOf(schema.getClass().getMethod("description").invoke(schema));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return schema.getClass().getSimpleName();
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte piece : digest) {
                hex.append(Character.forDigit((piece >> 4) & 0xF, 16))
                   .append(Character.forDigit(piece & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 sempre existe na JVM", e);
        }
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
