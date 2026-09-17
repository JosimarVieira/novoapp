package com.novoapp.common.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A forma normalizada de um texto: minuscula, sem acento, sem espaco sobrando
 * (ADR-0030).
 *
 * <p>Existe por duas razoes que apareceram juntas. A primeira: esta mesma
 * funcao estava copiada em tres lugares de producao -- o curto-circuito de
 * confirmacao, as respostas do onboarding e o casamento de categoria em
 * <code>nlu</code> -- e em mais tres nos testes. A segunda, e a que forcou:
 * nome de categoria e de item de lista passaram a ser <b>comparados</b> por esta
 * forma, e gravados por ela numa coluna propria. Duas implementacoes divergentes
 * de "mesma coisa" viram linha duplicada no banco.
 *
 * <p><b>Nao faz plural, nao faz raiz de palavra, nao faz distancia de edicao.</b>
 * "cafe" e "cafes" continuam sendo coisas diferentes, e isso esta decidido assim
 * na ADR-0030 -- aproximar por forma e barato e previsivel; aproximar por
 * sentido e outro problema, e o dado pra decide-lo sai da Etapa 5.
 */
public final class Normalization {

    private Normalization() {
    }

    /**
     * @return nulo quando a entrada e nula; string vazia quando a entrada e so
     *         espaco -- quem precisa recusar vazio recusa antes de chamar aqui
     */
    public static String of(String text) {
        if (text == null) {
            return null;
        }
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("\\s+", " ");
    }
}
