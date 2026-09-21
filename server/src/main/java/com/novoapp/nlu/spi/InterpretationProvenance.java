package com.novoapp.nlu.spi;

/**
 * Qual prompt e qual modelo produziram uma interpretacao (ADR-0035).
 *
 * <p>Existe porque as tres metricas da Etapa 5 sao calculadas <b>por</b>
 * {@code promptVersion}: a decisao daquela ADR foi nao congelar o prompt durante
 * a medicao -- congelar exigiria deixar dinheiro errado de pe por quatro
 * semanas -- e sim versionar, de modo que uma correcao abra uma janela nova em
 * vez de contaminar a anterior.
 *
 * <p>Atravessa a fronteira {@link MessageInterpreter} como String pura, pelo
 * mesmo motivo que {@link ToolCall} atravessa: nenhum tipo do provedor passa
 * daqui (ADR-0009).
 *
 * @param promptVersion impressao digital da parte <b>estatica</b> do que vai ao
 *        modelo -- textos de prompt, tools declaradas, descricao de cada
 *        parametro. Nunca o contexto injetado por mensagem (categorias e itens
 *        do household): aquilo varia por familia e por dia, e nao e versao de
 *        nada. Hash, e nao constante mantida a mao, porque constante depende de
 *        alguem lembrar de incrementa-la
 * @param modelName o modelo que respondeu, como configurado. Duas medicoes so
 *        sao comparaveis entre modelos se o gabarito nao mudar junto
 *        ([decisao aberta #3])
 */
public record InterpretationProvenance(String promptVersion, String modelName) {
}
