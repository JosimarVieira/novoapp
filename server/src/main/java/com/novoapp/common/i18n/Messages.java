package com.novoapp.common.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Todo texto voltado ao usuario, resolvido por idioma (ADR-0015).
 *
 * <p>A ADR decidiu isso para a "Etapa 1 em diante" e a regra e literal: nenhum
 * recibo, pergunta de confirmacao ou erro de chat e string no codigo. Quem
 * escreve texto usa {@link MessageKey} e este ponto resolve. A alternativa --
 * deixar literal agora e extrair quando ingles e espanhol forem pedidos -- foi
 * explicitamente descartada pela ADR, que previu o custo do retrofit.
 *
 * <p>Utilitario estatico, e nao bean CDI, pelo mesmo motivo de
 * {@link com.novoapp.conversation.ShortCircuit} e
 * {@link com.novoapp.common.tenancy.TenantContext}: nao ha estado nem colaborador
 * a injetar, e obrigar CDI aqui espalharia injecao por classes de texto que hoje
 * sao puras.
 *
 * <p><b>Idioma nao e moeda.</b> Valor continua formatado em pt-BR e em reais
 * mesmo quando o texto sair em outro idioma: a ADR-0015 poe multi-moeda
 * explicitamente fora de escopo, e {@code amount_cents} ja e agnostico.
 */
public final class Messages {

    private static final String BUNDLE = "messages";

    /**
     * Unico idioma com conteudo escrito durante a validacao (ADR-0015, decisao
     * 2). Ingles e espanhol entram como traducao deste arquivo quando houver
     * pedido real, na Etapa 6 -- traducao de conteudo, nao mudanca de codigo.
     */
    public static final Locale DEFAULT = Locale.of("pt", "BR");

    private static final Map<Locale, ResourceBundle> CACHE = new ConcurrentHashMap<>();

    private Messages() {
    }

    public static String get(Locale locale, MessageKey key, Object... arguments) {
        String pattern = bundleFor(locale).getString(key.key());
        if (arguments.length == 0) {
            // Sem argumento nao passa por MessageFormat: ele trata apostrofo como
            // escape, e um texto sem placeholder nao deveria correr esse risco.
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(arguments);
    }

    /**
     * Bundle do idioma pedido, caindo em {@link #DEFAULT} quando ele nao existe.
     *
     * <p>A queda e explicita, e nao a do {@code ResourceBundle}: a dele usa o
     * locale <em>da JVM</em> antes do bundle base, o que faria a resposta sair no
     * idioma do servidor em vez do idioma da familia -- erro silencioso, do tipo
     * que a propria ADR-0015 lista como consequencia negativa.
     */
    private static ResourceBundle bundleFor(Locale locale) {
        return CACHE.computeIfAbsent(locale == null ? DEFAULT : locale, requested -> {
            try {
                return ResourceBundle.getBundle(BUNDLE, requested, new PerLocaleOnly());
            } catch (MissingResourceException absent) {
                return ResourceBundle.getBundle(BUNDLE, DEFAULT, new PerLocaleOnly());
            }
        });
    }

    /** Desliga a cadeia de fallback embutida: ou existe o arquivo daquele idioma, ou nao existe. */
    private static final class PerLocaleOnly extends ResourceBundle.Control {

        @Override
        public java.util.List<Locale> getCandidateLocales(String baseName, Locale locale) {
            return java.util.List.of(locale);
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    }
}
