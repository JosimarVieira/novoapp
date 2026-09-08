package com.novoapp.nlu.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * "convidar Bruno, +5511900000002" (ADR-0020).
 *
 * <p>Nao esta na lista de tools que a ADR-0004 enumera -- aquela lista descreve
 * o mecanismo com os fluxos de dominio conhecidos na epoca, e a ADR-0020 e
 * posterior. Ela exige que o OWNER peca o convite ao bot, e o comando parte de
 * numero ja vinculado: atravessa o pipeline de interpretacao como qualquer
 * outra mensagem, logo precisa de tool. Registrado em sdd-modulo-nlu.md.
 *
 * <p>O que este caminho produz e o convite e o link; a entrega e humana -- o
 * sistema nao consegue iniciar conversa com quem nunca falou com o bot
 * (limitacao da Bot API, ADR-0020).
 */
public final class InviteMemberTool {

    public static final String NAME = "convidarMembro";
    public static final String MEMBER_NAME_PARAMETER = "nome";
    public static final String PHONE_PARAMETER = "telefone";
    public static final String CONFIDENCE_PARAMETER = "confianca";

    private InviteMemberTool() {
    }

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Cria um convite para alguem entrar nesta familia e devolve o link para quem "
                        + "convidou repassar.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(MEMBER_NAME_PARAMETER, JsonStringSchema.builder()
                                .description("Nome da pessoa convidada, como quem convida a chamou.")
                                .build())
                        .addProperty(PHONE_PARAMETER, JsonStringSchema.builder()
                                .description("Telefone da pessoa convidada, com codigo do pais, no formato "
                                        + "+5511999999999.")
                                .build())
                        .addProperty(CONFIDENCE_PARAMETER, JsonNumberSchema.builder()
                                .description("De 0 a 1, o quanto voce tem certeza desta interpretacao.")
                                .build())
                        .required(MEMBER_NAME_PARAMETER, PHONE_PARAMETER, CONFIDENCE_PARAMETER)
                        .build())
                .build();
    }
}
