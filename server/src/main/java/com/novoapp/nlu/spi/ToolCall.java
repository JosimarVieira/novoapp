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
