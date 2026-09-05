package com.novoapp.identity.onboarding;

import java.util.List;

/**
 * Todo texto do onboarding, num lugar so.
 *
 * <p>Estes textos nunca passam por LLM nem por politica de confianca: e arvore
 * de decisao fixa (ADR-0020). Ficam em <code>identity</code>, e nao em
 * <code>conversation</code>, porque nao ha interpretacao nenhuma envolvida --
 * sao as perguntas que o proprio modulo faz.
 *
 * <p>Sao os unicos textos deste modulo escritos com acentuacao completa: e o que
 * o usuario le. Comentario e SQL seguem sem acento por seguranca de encoding.
 */
public final class OnboardingMessages {

    private OnboardingMessages() {
    }

    /**
     * Primeira coisa que qualquer numero desconhecido recebe, seja a mensagem um
     * "/start", um "mercado 50" ou um "quero entrar na familia do Silva".
     *
     * <p>E deliberadamente um texto so pros tres casos: sem estado e sem LLM nao
     * ha como distinguir a intencao por tras da primeira mensagem, e adivinhar
     * por palavra-chave seria o parser fragil que a ADR-0004 descartou. Este
     * texto responde aos tres cenarios de `vinculo-de-identidade.feature` que
     * caem aqui -- explica o produto, avisa que so se entra em familia existente
     * por convite, e oferece criar a propria.
     */
    public static final String WELCOME = """
            Oi! Por aqui você registra as despesas, a lista de mercado e as tarefas da sua família, \
            sem precisar abrir aplicativo nenhum.

            Para começar, seu número precisa estar vinculado a uma família. Entrar numa família que já \
            existe só é possível por convite de quem já faz parte dela — peça o link para essa pessoa.

            Quer criar uma família nova? Responda sim ou não.""";

    public static final String DECLINED = """
            Tudo bem. Quando quiser criar a sua família, é só me dizer. Se alguém da sua família já usa \
            o aplicativo, peça o link de convite para essa pessoa.""";

    public static final String ASK_HOUSEHOLD_NAME =
            "Como você quer chamar a sua família? Pode ser o sobrenome, por exemplo: Silva";

    public static String householdCreated(String householdName) {
        return """
                Pronto: a família "%s" foi criada e você é o responsável por ela.

                Quer terminar a configuração por aqui mesmo, pelo chat, ou prefere pelo aplicativo?"""
                .formatted(householdName);
    }

    /**
     * Cenario "Escolhe terminar configuracao no aplicativo, antes da Etapa 4
     * existir": o aplicativo web so chega na Etapa 4 do ROADMAP (ADR-0021).
     */
    public static final String APP_NOT_AVAILABLE_YET = """
            O aplicativo web ainda não está disponível nesta etapa — ele vem depois.

            Dá para fazer tudo por aqui pelo chat. Quer continuar a configuração por aqui?""";

    public static final String CONTINUING_BY_CHAT =
            "Combinado, seguimos por aqui. Para registrar uma despesa, é só mandar algo como: mercado 50";

    public static String inviteAskContact(String householdName) {
        return """
                Você foi convidado para a família "%s".

                Para confirmar que o convite é seu, compartilhe o seu contato usando o botão do Telegram."""
                .formatted(householdName);
    }

    public static String inviteAccepted(String householdName) {
        return "Pronto: você agora faz parte da família \"%s\".".formatted(householdName);
    }

    /** ADR-0007: quem tem mais de um vinculo precisa saber como trocar o ativo. */
    public static String inviteAcceptedWithOtherHouseholds(String householdName, String activeHouseholdName) {
        return """
                Pronto: você agora faz parte da família "%s" também.

                Suas mensagens continuam indo para a família "%s". Para trocar, diga: usar %s"""
                .formatted(householdName, activeHouseholdName, householdName);
    }

    public static final String INVITE_PHONE_MISMATCH =
            "Este convite não é para o seu número. Confira com quem te convidou.";

    public static final String INVITE_EXPIRED =
            "Este convite expirou. Peça um convite novo para quem te convidou.";

    public static final String INVITE_ALREADY_USED = "Este convite já foi usado.";

    public static final String INVITE_NOT_FOUND = "Não encontrei este convite.";

    /** ADR-0007: pessoa com mais de um household e nenhum ativo. */
    public static String chooseHousehold(List<String> householdNames) {
        StringBuilder text = new StringBuilder(
                "Você participa de mais de uma família. Para qual delas é esta mensagem?\n");
        for (int index = 0; index < householdNames.size(); index++) {
            text.append("\n").append(index + 1).append(") ").append(householdNames.get(index));
        }
        return text.toString();
    }
}
