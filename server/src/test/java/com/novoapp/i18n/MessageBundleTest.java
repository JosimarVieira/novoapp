package com.novoapp.i18n;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ADR-0015 travada no build.
 *
 * <p>Ela foi aceita em 2026-08-31, valia "da Etapa 1 em diante", e mesmo assim
 * duas etapas inteiras foram entregues com texto literal espalhado pelo codigo
 * -- ninguem percebeu porque nada cobrava. Este teste e o que faltava: e mais
 * barato que revisao, e nao esquece.
 *
 * <p>Unidade pura, sem Quarkus: le o arquivo de mensagens e o fonte das duas
 * classes de texto, e nao precisa de banco nem de contexto CDI pra nenhuma das
 * duas coisas.
 */
class MessageBundleTest {

    /**
     * As unicas duas classes autorizadas a produzir texto voltado ao usuario.
     * Concentrar isso em dois arquivos e o que torna a regra da ADR-0015
     * verificavel -- espalhado, so daria pra conferir por leitura.
     */
    private static final List<Path> TEXT_CLASSES = List.of(
            Path.of("src/main/java/com/novoapp/conversation/ReceiptFormatter.java"),
            Path.of("src/main/java/com/novoapp/identity/onboarding/OnboardingMessages.java"));

    private static final Path BUNDLE = Path.of("src/main/resources/messages_pt_BR.properties");

    /**
     * Literal de ate 24 caracteres passa: e formatacao ("R$ ", "dd/MM/yyyy",
     * "\n"), nao mensagem. A menor mensagem de verdade do arquivo tem mais que o
     * dobro disso, entao a margem e larga -- o limite existe pra pegar quem
     * inlina um recibo, nao pra brigar com separador.
     */
    private static final int MAX_LITERAL = 24;

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    @DisplayName("toda MessageKey tem texto no arquivo pt-BR")
    void everyKeyIsTranslated() throws IOException {
        Properties bundle = loadBundle();
        List<String> missing = Arrays.stream(MessageKey.values())
                .map(MessageKey::key)
                .filter(key -> !bundle.containsKey(key))
                .toList();

        assertThat(missing)
                .as("chaves declaradas em MessageKey e ausentes de messages_pt_BR.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("o arquivo pt-BR nao tem chave que ninguem usa")
    void bundleHasNoOrphanKey() throws IOException {
        Set<String> declared = Arrays.stream(MessageKey.values())
                .map(MessageKey::key)
                .collect(Collectors.toSet());

        assertThat(loadBundle().stringPropertyNames())
                .as("chaves no arquivo sem constante correspondente em MessageKey")
                .allMatch(declared::contains);
    }

    @Test
    @DisplayName("todo texto resolve, e nenhum deixa placeholder por preencher")
    void everyMessageResolves() {
        for (MessageKey key : MessageKey.values()) {
            String pattern = Messages.get(Messages.DEFAULT, key);
            assertThat(pattern).as("texto de %s", key).isNotBlank();

            // Placeholder e contrato: quem chama precisa passar tantos argumentos
            // quantos o texto pede. Aqui so se confere que a numeracao e continua
            // a partir de zero -- {0} e {2} sem {1} seria erro de escrita que so
            // apareceria na frente do usuario.
            int placeholders = new MessageFormat(pattern, Messages.DEFAULT).getFormatsByArgumentIndex().length;
            for (int index = 0; index < placeholders; index++) {
                assertThat(pattern).as("texto de %s deveria usar {%d}", key, index)
                        .contains("{" + index + "}");
            }
        }
    }

    @Test
    @DisplayName("idioma sem arquivo cai no pt-BR, e nao no idioma do servidor")
    void unknownLocaleFallsBackToDefault() {
        // A queda do ResourceBundle usaria o locale da JVM antes do bundle base --
        // a resposta sairia no idioma do servidor. Erro silencioso, do tipo que a
        // propria ADR-0015 lista como consequencia negativa.
        assertThat(Messages.get(java.util.Locale.forLanguageTag("ja-JP"), MessageKey.NOT_UNDERSTOOD))
                .isEqualTo(Messages.get(Messages.DEFAULT, MessageKey.NOT_UNDERSTOOD));
    }

    @Test
    @DisplayName("nenhum texto voltado ao usuario e literal no codigo (ADR-0015)")
    void noUserFacingLiteralInCode() throws IOException {
        for (Path source : TEXT_CLASSES) {
            assertThat(source).exists();
            String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));

            Matcher literals = STRING_LITERAL.matcher(code);
            while (literals.find()) {
                String literal = literals.group(1);
                assertThat(literal.length())
                        .as("%s tem um literal de %d caracteres: \"%s\" -- texto de usuario vive em "
                                        + "messages_pt_BR.properties, nao aqui (ADR-0015)",
                                source.getFileName(), literal.length(), literal)
                        .isLessThanOrEqualTo(MAX_LITERAL);
            }
        }
    }

    /** Javadoc e comentario citam texto de exemplo; o que se mede e o codigo. */
    private String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private Properties loadBundle() throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(BUNDLE, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
