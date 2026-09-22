package com.novoapp.nlu.spi;

import java.util.Map;

/**
 * A chamada de funcao que o modelo escolheu, ainda crua: nome da tool e
 * argumentos como vieram, sem id resolvido e sem validacao.
 *
 * <p>E um mapa, e nao um record por tool, de proposito: este tipo atravessa a
 * fronteira com o provedor de LLM, e a fronteira nao pode saber quais tools
 * existem -- se soubesse, cada tool nova exigiria mexer no adaptador. Quem
 * traduz mapa em intencao tipada e {@code NluService}, do lado de ca.
 */
public record ToolCall(String toolName, Map<String, Object> arguments) {

    public String text(String parameter) {
        Object value = arguments.get(parameter);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public Long integer(String parameter) {
        Object value = arguments.get(parameter);
        return switch (value) {
            case null -> null;
            case Number number -> number.longValue();
            default -> {
                try {
                    yield Long.valueOf(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    /**
     * Numero decimal exato, construido a partir da forma textual e nunca de um
     * {@code double}: <code>new BigDecimal(49.90d)</code> vale
     * 49.899999999999998578..., e isto aqui carrega dinheiro.
     */
    public java.math.BigDecimal decimal(String parameter) {
        Object value = arguments.get(parameter);
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double number(String parameter) {
        Object value = arguments.get(parameter);
        return switch (value) {
            case null -> null;
            case Number number -> number.doubleValue();
            default -> {
                try {
                    yield Double.valueOf(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    /**
     * Um array de texto simples -- os itens que <code>fecharCompra</code> diz
     * terem sido comprados.
     *
     * <p>Ausente e vazio sao a mesma coisa aqui, e isso e contrato e nao
     * descuido: em <code>fecharCompra</code> lista vazia significa "comprei
     * tudo" (ADR-0031), e um modelo que omite o parametro quis dizer exatamente
     * isso.
     */
    public java.util.List<String> strings(String parameter) {
        Object value = arguments.get(parameter);
        if (value instanceof java.util.List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(item -> String.valueOf(item).trim())
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> objects(String parameter) {
        Object value = arguments.get(parameter);
        if (value instanceof java.util.List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return java.util.List.of();
    }
}
