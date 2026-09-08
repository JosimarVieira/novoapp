package com.novoapp.identity.onboarding;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Todo texto do onboarding, num lugar so.
 *
 * <p>Estes textos nunca passam por LLM nem por politica de confianca: e arvore
 * de decisao fixa (ADR-0020). Ficam em <code>identity</code>, e nao em
 * <code>conversation</code>, porque nao ha interpretacao nenhuma envolvida --
 * sao as perguntas que o proprio modulo faz.
 *
 * <p><b>Nenhum texto e literal aqui</b> (ADR-0015): esta classe so escolhe qual
 * mensagem cabe em cada passo, e o conteudo vem de {@link Messages}. Um teste
 * trava isso -- literal longo neste arquivo quebra o build.
 *
 * <p>Quem esta no onboarding ainda nao tem <code>member</code>, logo nao tem
 * <code>preferred_locale</code>: todo texto daqui sai em
 * {@link Messages#DEFAULT}. Escolher idioma antes de existir pessoa exigiria
 * perguntar o idioma como primeiro passo do onboarding, o que a ADR-0015 nao
 * pede e nenhum cenario descreve.
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
    public static String welcome() {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_WELCOME);
    }

    public static String declined() {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_DECLINED);
    }

    public static String askHouseholdName() {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_ASK_HOUSEHOLD_NAME);
    }

    public static String householdCreated(String householdName) {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_HOUSEHOLD_CREATED, householdName);
    }

    /**
     * Cenario "Escolhe terminar configuracao no aplicativo, antes da Etapa 4
     * existir": o aplicativo web so chega na Etapa 4 do ROADMAP (ADR-0021).
     */
    public static String appNotAvailableYet() {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_APP_NOT_AVAILABLE);
    }

    public static String continuingByChat() {
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_CONTINUING_BY_CHAT);
    }

    public static String inviteAskContact(String householdName) {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_ASK_CONTACT, householdName);
    }

    public static String inviteAccepted(String householdName) {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_ACCEPTED, householdName);
    }

    /** ADR-0007: quem tem mais de um vinculo precisa saber como trocar o ativo. */
    public static String inviteAcceptedWithOtherHouseholds(String householdName, String activeHouseholdName) {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_ACCEPTED_OTHER_HOUSEHOLDS,
                householdName, activeHouseholdName);
    }

    public static String invitePhoneMismatch() {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_PHONE_MISMATCH);
    }

    public static String inviteExpired() {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_EXPIRED);
    }

    public static String inviteAlreadyUsed() {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_ALREADY_USED);
    }

    public static String inviteNotFound() {
        return Messages.get(Messages.DEFAULT, MessageKey.INVITE_NOT_FOUND);
    }

    /** ADR-0007: pessoa com mais de um household e nenhum ativo. */
    public static String chooseHousehold(List<String> householdNames) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < householdNames.size(); index++) {
            lines.add(Messages.get(Messages.DEFAULT, MessageKey.OPTION_LINE,
                    index + 1, householdNames.get(index)));
        }
        return Messages.get(Messages.DEFAULT, MessageKey.ONBOARDING_CHOOSE_HOUSEHOLD,
                String.join("\n", lines));
    }

    /** O idioma de todo texto de onboarding. Ver a nota de classe. */
    public static Locale locale() {
        return Messages.DEFAULT;
    }
}
