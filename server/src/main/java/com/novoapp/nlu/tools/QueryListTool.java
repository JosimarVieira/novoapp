package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/** "o que esta faltando?" (ADR-0004, tool <code>consultarLista</code>). */
public final class QueryListTool {

    public static final String NAME = "consultarLista";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private QueryListTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Responde o que ainda esta faltando na lista de compras da familia.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao.")
                                .build())
                        .required(CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
