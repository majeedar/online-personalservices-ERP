package edu.university.ops.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.DayPart;
import edu.university.ops.shared.batch.BatchJobRun;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import edu.university.ops.shared.workflow.WorkflowEnums.TaskStatus;
import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.travel.domain.TravelExpense;
import edu.university.ops.travel.domain.TravelStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every server text has a German translation (ADR-020). Templates are found in the
 * sources (Text.of("..."), literal exception, issue and not-found messages); texts
 * built from codes are listed below. The browser sweep checks the rest at runtime.
 */
class TranslationCompletenessTest {

    private static final String LITERALS = "(\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]|\\\\.)*\")*)";
    private static final List<Pattern> SOURCES = List.of(
            Pattern.compile("Text\\.of\\(\\s*" + LITERALS),
            Pattern.compile("new BusinessException\\(\\s*ErrorCode\\.\\w+,\\s*" + LITERALS),
            Pattern.compile("BusinessException\\.notFound\\(\\s*ErrorCode\\.\\w+,\\s*" + LITERALS),
            Pattern.compile("new Issue\\(\\s*ErrorCode\\.\\w+,\\s*" + LITERALS));
    private static final Pattern COLUMNS = Pattern.compile("columns\\(((?:\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*,?)+)\\)");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Texts built from codes at runtime (statuses, types, names used inside sentences). */
    static Set<String> codeTexts() {
        Set<String> texts = new TreeSet<>();
        Stream.of(AbsenceStatus.values()).map(Enum::name).forEach(texts::add);
        Stream.of(TravelStatus.values()).map(Enum::name).forEach(texts::add);
        Stream.of(BatchJobRun.Status.values()).map(Enum::name).forEach(texts::add);
        Stream.of(TravelExpense.Type.values()).map(Enum::name).forEach(texts::add);
        Stream.of(Role.values()).map(Enum::name).forEach(texts::add);
        Stream.of(TaskStatus.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).forEach(texts::add);
        Stream.of(InstanceStatus.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).forEach(texts::add);
        Stream.of(TimeEntry.Type.values()).map(t -> t.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .forEach(texts::add);
        Arrays.stream(DayPart.values()).filter(DayPart::isHalf).map(p -> p.name().toLowerCase(Locale.ROOT))
                .forEach(texts::add);
        texts.addAll(List.of("SUPERVISOR_APPROVAL", "FINANCIAL_APPROVAL", "TRAVEL_OFFICE_REVIEW",
                "TIME_CORRECTION_APPROVAL", "TRAVEL_ERP_EXPORT", "TRAVEL_EXPORT", "TRAVEL_SETTLEMENT_EXPORT",
                "FINANCE_POSTING", "overdue", "on time"));
        // Seeded public holidays (demo calendar).
        texts.addAll(List.of("New Year's Day", "Good Friday", "Easter Monday", "Labour Day", "Ascension Day",
                "Whit Monday", "Day of Unity", "Christmas Day", "Second Day of Christmas"));
        // Seeded leave types: the name (descriptions) and lower case (inside sentences).
        for (String name : List.of("Annual leave", "Flex day (time off in lieu)", "Sick leave", "Special leave",
                "Unpaid leave")) {
            texts.add(name);
            texts.add(name.toLowerCase(Locale.ROOT));
        }
        return texts;
    }

    static Set<String> sourceTemplates() throws IOException {
        Set<String> templates = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Comments hold examples, not texts.
                String source = Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("(?m)^\\s*//.*$", "");
                // Report columns: columns("Unit", "Employees", ...), every literal on its own.
                Matcher columns = COLUMNS.matcher(source);
                while (columns.find()) {
                    Matcher literal = STRING.matcher(columns.group(1));
                    while (literal.find()) {
                        templates.add(literal.group(1));
                    }
                }
                for (Pattern pattern : SOURCES) {
                    Matcher m = pattern.matcher(source);
                    while (m.find()) {
                        templates.add(join(m.group(1)));
                    }
                }
            }
        }
        templates.add("{what} was not found.");
        // Templates without words (e.g. "{from} – {to}") need no translation.
        templates.removeIf(t -> !t.replaceAll("\\{\\w+}", "").matches("(?s).*\\p{L}{2,}.*"));
        return templates;
    }

    private static String join(String literals) {
        StringBuilder out = new StringBuilder();
        Matcher m = STRING.matcher(literals);
        while (m.find()) {
            out.append(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return out.toString();
    }

    @Test
    void everyServerTextHasAGermanTranslation() throws IOException {
        Set<String> missing = new TreeSet<>(sourceTemplates());
        missing.addAll(codeTexts());
        missing.removeIf(Translator::hasGerman);
        if (System.getProperty("i18n.dump") != null) {
            // mvn test -Dtest=TranslationCompletenessTest -Di18n.dump: missing texts as JSON, for translating
            Files.writeString(Path.of("target/i18n-missing.json"), new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(missing));
        }
        assertThat(missing).as("add these to src/main/resources/i18n/de.json").isEmpty();
    }

    @Test
    void germanTranslationsKeepThePlaceholders() {
        Pattern placeholder = Pattern.compile("\\{\\w+}");
        Translator.germanTexts().forEach((english, german) -> {
            Set<String> expected = new TreeSet<>(placeholder.matcher(english).results().map(r -> r.group()).toList());
            Set<String> actual = new TreeSet<>(placeholder.matcher(german).results().map(r -> r.group()).toList());
            assertThat(actual).as("placeholders of %s", english).isEqualTo(expected);
        });
    }

    @Test
    void rendersGermanPseudoAndEnglish() {
        Text text = Text.of("Your {type} for {period} has been approved.", "type", Text.of("annual leave"),
                "period", "01.03.2027");
        assertThat(text.english()).isEqualTo("Your annual leave for 01.03.2027 has been approved.");
        assertThat(text.render(Translator.PSEUDO)).startsWith("⟦Ýóúr ⟦áññúál léávé⟧ fór 01.03.2027");
        assertThat(Translator.render(Text.of("{n} day(s)", "n", new java.math.BigDecimal("1.5")), Locale.GERMAN))
                .startsWith("1,5");
        assertThat(Translator.forAcceptLanguage("de-DE,de;q=0.9,en;q=0.8")).isEqualTo(Locale.GERMAN);
        assertThat(Translator.forAcceptLanguage("fr-FR")).isEqualTo(Locale.ENGLISH);
        assertThat(Translator.forAcceptLanguage(null)).isEqualTo(Locale.ENGLISH);
    }
}
