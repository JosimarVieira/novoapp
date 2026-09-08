package com.novoapp.conversation;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A tabela da ADR-0004, traduzida em duas faixas.
 *
 * <table>
 *   <tr><td>Confianca alta, entidades resolvidas</td><td>Executa e responde recibo</td></tr>
 *   <tr><td>Confianca media, ou categoria inexistente</td><td>UMA pergunta com opcoes numeradas</td></tr>
 *   <tr><td>Confianca baixa, ou nenhuma tool escolhida</td><td>Pergunta aberta curta, sem adivinhar</td></tr>
 * </table>
 *
 * <p>Os dois limiares sao <b>config global do app, provisoria</b>, editavel e
 * redeployavel -- nunca constante escondida no codigo, e nunca preferencia por
 * household ([decisao aberta #7]: "precisao de IA nao e gosto de familia"). Os
 * valores de hoje sao palpite declarado, nao calibracao: o numero de verdade sai
 * da Etapa 5, com dado real, e a propria ADR-0004 registra que o
 * <code>confidence</code> que o modelo reporta e mal calibrado por natureza --
 * essa e a fraqueza central da decisao, nao um detalhe desta classe.
 */
@ApplicationScoped
public class ConfidencePolicy {

    @ConfigProperty(name = "novoapp.conversation.confidence.high", defaultValue = "0.8")
    double high;

    @ConfigProperty(name = "novoapp.conversation.confidence.low", defaultValue = "0.4")
    double low;

    public enum Level {
        HIGH,
        MEDIUM,
        LOW
    }

    public Level levelOf(double confidence) {
        if (confidence >= high) {
            return Level.HIGH;
        }
        if (confidence >= low) {
            return Level.MEDIUM;
        }
        return Level.LOW;
    }
}
