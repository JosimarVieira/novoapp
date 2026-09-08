package com.novoapp.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O vínculo ADR ↔ SDD travado no build.
 *
 * <p>Existe por causa de uma falha concreta: a
 * [ADR-0015](../../docs/01-adr/0015-internacionalizacao.md) foi aceita em
 * 2026-08-31, valia "da Etapa 1 em diante", e duas etapas inteiras foram
 * entregues contrariando-a. Ninguém percebeu porque as etapas navegaram por
 * lista curada de ADRs — e ela não estava em nenhuma das listas. Era a única ADR
 * aceita que nenhum documento e nenhum código citavam, e ninguém tinha como
 * saber disso sem ir procurar.
 *
 * <p>Duas direções, porque o furo tem dois lados. SDD sem ADR é design sem
 * lastro; ADR sem SDD é decisão que ninguém implementou e que vai ser
 * redescoberta tarde. Este teste cobra as duas.
 *
 * <p>Roda como teste de arquitetura porque é a mesma classe de regra do
 * {@link ModuleBoundariesTest}: coisa que revisão de código não pega de forma
 * confiável, e que é barata de verificar. Sem Quarkus, sem banco — só lê
 * arquivo.
 */
class DocumentationCoherenceTest {

    private static final Path ADR_DIR = Path.of("../docs/01-adr");
    private static final Path ARCHITECTURE_DIR = Path.of("../docs/02-arquitetura");

    /**
     * ADR aceita que nenhum documento de arquitetura reflete, <b>com o motivo</b>.
     *
     * <p>Entrar aqui é ato deliberado: exige mexer neste arquivo e escrever por
     * quê. É o oposto de uma ADR sumir de vista sem ninguém decidir que ela
     * sumiria — que foi exatamente o que aconteceu com a ADR-0015.
     */
    private static final Map<Integer, String> WITHOUT_SDD = new LinkedHashMap<>(Map.of(
            6, "Assinatura por household e canal proativo: decide cobrança (Etapa 6) e proatividade "
                    + "(Etapa 8). Não há módulo nem SDD onde caiba, e escrever um agora seria a ficção "
                    + "que o CLAUDE.md proíbe para as Etapas 4+. O autor confirmou em 2026-09-08 que só "
                    + "volta a isso depois de usar a aplicação na família.",
            28, "Recorrência de tarefa: decidida em 2026-09-08 para não virar premissa silenciosa ao "
                    + "escrever a .feature. O módulo tasks é Etapa 2b e ainda não tem SDD; quando tiver, "
                    + "esta linha sai."));

    private static final Pattern FRONTMATTER_FIELD =
            Pattern.compile("^([a-z_]+):[ \\t]*(.*)$", Pattern.MULTILINE);
    private static final Pattern ADR_REFERENCE = Pattern.compile("ADR-(\\d{4})");

    @Test
    @DisplayName("toda ADR aceita é refletida por algum documento de arquitetura, ou tem exceção justificada")
    void everyAcceptedAdrIsReflectedSomewhere() throws IOException {
        Set<Integer> referenced = new TreeSet<>();
        for (Path doc : architectureDocs()) {
            Matcher matcher = ADR_REFERENCE.matcher(Files.readString(doc, StandardCharsets.UTF_8));
            while (matcher.find()) {
                referenced.add(Integer.parseInt(matcher.group(1)));
            }
        }

        List<String> orphans = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, String>> adr : acceptedAdrs().entrySet()) {
            if (!referenced.contains(adr.getKey()) && !WITHOUT_SDD.containsKey(adr.getKey())) {
                orphans.add("ADR-%04d (%s)".formatted(adr.getKey(), adr.getValue().get("file")));
            }
        }

        assertThat(orphans)
                .as("ADR aceita que nenhum SDD reflete. Ou o design entra num SDD, ou a ADR entra em "
                        + "WITHOUT_SDD com o motivo escrito. Silêncio não é opção -- foi assim que a "
                        + "ADR-0015 atravessou duas etapas sendo contrariada.")
                .isEmpty();
    }

    @Test
    @DisplayName("todo SDD declara em quais ADRs se apoia")
    void everySddDeclaresItsAdrs() throws IOException {
        List<String> withoutBacking = new ArrayList<>();
        for (Path doc : architectureDocs()) {
            Map<String, String> frontmatter = frontmatterOf(doc);
            if (!"sdd".equals(frontmatter.get("tipo"))) {
                continue;
            }
            if (adrListOf(doc).isEmpty()) {
                withoutBacking.add(doc.getFileName().toString());
            }
        }

        assertThat(withoutBacking)
                .as("SDD sem nenhuma ADR no frontmatter é design sem lastro: alguém decidiu, ninguém "
                        + "registrou onde a decisão pode ser superada.")
                .isEmpty();
    }

    @Test
    @DisplayName("ADR citada por um SDD existe e está aceita")
    void sddsOnlyCiteAcceptedAdrs() throws IOException {
        Map<Integer, Map<String, String>> accepted = acceptedAdrs();
        Map<Integer, Map<String, String>> all = adrs();

        List<String> problems = new ArrayList<>();
        for (Path doc : architectureDocs()) {
            for (Integer number : adrListOf(doc)) {
                if (!all.containsKey(number)) {
                    problems.add("%s cita ADR-%04d, que não existe".formatted(doc.getFileName(), number));
                } else if (!accepted.containsKey(number)) {
                    problems.add("%s se apoia na ADR-%04d, que está %s"
                            .formatted(doc.getFileName(), number, all.get(number).get("status")));
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("a numeração das ADRs é sequencial, sem buraco e sem reuso")
    void adrNumbersAreSequential() throws IOException {
        Map<Integer, Map<String, String>> all = adrs();
        List<Integer> numbers = new ArrayList<>(new TreeSet<>(all.keySet()));

        for (int index = 0; index < numbers.size(); index++) {
            assertThat(numbers.get(index))
                    .as("numeração das ADRs: esperado %04d na posição %d", index + 1, index)
                    .isEqualTo(index + 1);
        }

        for (Map.Entry<Integer, Map<String, String>> adr : all.entrySet()) {
            assertThat(adr.getValue().get("numero"))
                    .as("frontmatter de %s", adr.getValue().get("file"))
                    .isEqualTo(String.valueOf(adr.getKey()));
        }
    }

    @Test
    @DisplayName("supera e superada_por são recíprocos, e nada aponta para ADR inexistente")
    void supersessionIsReciprocal() throws IOException {
        Map<Integer, Map<String, String>> all = adrs();
        List<String> problems = new ArrayList<>();

        all.forEach((number, adr) -> {
            for (String field : List.of("depende_de", "supera", "superada_por")) {
                for (Integer target : numbersIn(adr.getOrDefault(field, ""))) {
                    if (!all.containsKey(target)) {
                        problems.add("ADR-%04d.%s aponta para ADR-%04d, que não existe"
                                .formatted(number, field, target));
                        continue;
                    }
                    String mirror = field.equals("supera") ? "superada_por"
                            : field.equals("superada_por") ? "supera" : null;
                    if (mirror != null
                            && !numbersIn(all.get(target).getOrDefault(mirror, "")).contains(number)) {
                        problems.add("ADR-%04d.%s = %04d, mas ADR-%04d.%s não aponta de volta"
                                .formatted(number, field, target, target, mirror));
                    }
                }
            }
        });

        assertThat(problems).isEmpty();
    }

    // ------------------------------------------------------------------

    private List<Path> architectureDocs() throws IOException {
        try (Stream<Path> files = Files.list(ARCHITECTURE_DIR)) {
            return files.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }
    }

    private Map<Integer, Map<String, String>> adrs() throws IOException {
        Map<Integer, Map<String, String>> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(ADR_DIR)) {
            for (Path path : files.sorted().toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".md") || name.equals("TEMPLATE.md")) {
                    continue;
                }
                Map<String, String> frontmatter = frontmatterOf(path);
                frontmatter.put("file", name);
                found.put(Integer.parseInt(name.substring(0, 4)), frontmatter);
            }
        }
        return found;
    }

    private Map<Integer, Map<String, String>> acceptedAdrs() throws IOException {
        Map<Integer, Map<String, String>> accepted = new LinkedHashMap<>();
        adrs().forEach((number, adr) -> {
            if ("aceita".equals(adr.get("status"))) {
                accepted.put(number, adr);
            }
        });
        return accepted;
    }

    /**
     * Frontmatter chapado: valor inline vira string, lista YAML vira a mesma
     * string com os itens juntos. Basta para o que se confere aqui, e evita
     * trazer um parser de YAML só para isto.
     */
    private Map<String, String> frontmatterOf(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (!content.startsWith("---")) {
            return Map.of();
        }
        String raw = content.substring(3, content.indexOf("\n---", 3));

        Map<String, String> fields = new LinkedHashMap<>();
        String current = null;
        for (String line : raw.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("- ") && current != null) {
                fields.merge(current, trimmed.substring(2).strip(), (a, b) -> a + " " + b);
                continue;
            }
            Matcher field = FRONTMATTER_FIELD.matcher(trimmed);
            if (field.matches()) {
                current = field.group(1);
                fields.put(current, field.group(2).strip());
            }
        }
        return fields;
    }

    private List<Integer> adrListOf(Path doc) throws IOException {
        return numbersIn(frontmatterOf(doc).getOrDefault("adrs", ""));
    }

    private List<Integer> numbersIn(String value) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = ADR_REFERENCE.matcher(value);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        return numbers;
    }
}
