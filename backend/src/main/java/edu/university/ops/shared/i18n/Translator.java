package edu.university.ops.shared.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Renders {@link Text}s (ADR-020). English templates are the keys; German comes from
 * {@code i18n/de.json}. A missing entry falls back to English, and
 * {@code TranslationCompletenessTest} fails the build if one is missing.
 *
 * <p>The test language {@code qps} (pseudo) shows every template as
 * {@code ⟦Wórkíñg tímé⟧}, like the frontend, so the browser sweep can tell texts that
 * went through the dictionary from those that did not.
 */
public final class Translator {

    public static final Locale GERMAN = Locale.GERMAN;
    public static final Locale PSEUDO = Locale.forLanguageTag("qps-ploc");
    public static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, GERMAN, PSEUDO);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> GERMAN_TEXTS = load("i18n/de.json");
    private static final Map<Character, Character> ACCENTS = Map.ofEntries(
            Map.entry('a', 'á'), Map.entry('e', 'é'), Map.entry('i', 'í'), Map.entry('o', 'ó'), Map.entry('u', 'ú'),
            Map.entry('n', 'ñ'), Map.entry('c', 'ç'), Map.entry('y', 'ý'), Map.entry('A', 'Á'), Map.entry('E', 'É'),
            Map.entry('I', 'Í'), Map.entry('O', 'Ó'), Map.entry('U', 'Ú'), Map.entry('N', 'Ñ'), Map.entry('C', 'Ç'),
            Map.entry('Y', 'Ý'));

    private Translator() {
    }

    /** The language of the current request (Accept-Language), English outside a request. */
    public static Locale currentLocale() {
        var context = LocaleContextHolder.getLocaleContext();
        // Not the JVM default: a scheduled job on a German server must not change stored texts.
        return context == null || context.getLocale() == null ? Locale.ENGLISH : supported(context.getLocale());
    }

    /** Maps any locale to a supported one: German, the test language, or English. */
    public static Locale supported(Locale locale) {
        if (locale == null) {
            return Locale.ENGLISH;
        }
        return switch (locale.getLanguage()) {
            case "de" -> GERMAN;
            case "qps" -> PSEUDO;
            default -> Locale.ENGLISH;
        };
    }

    /** The best supported language for an Accept-Language header; English if there is none. */
    public static Locale forAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                Locale locale = supported(Locale.forLanguageTag(range.getRange()));
                if (!locale.equals(Locale.ENGLISH) || range.getRange().startsWith("en")) {
                    return locale;
                }
            }
        } catch (IllegalArgumentException e) {
            // malformed header: English
        }
        return Locale.ENGLISH;
    }

    /** Language code stored for an employee ("en" / "de"); anything else counts as English. */
    public static Locale forLanguageCode(String code) {
        return "de".equals(code) ? GERMAN : Locale.ENGLISH;
    }

    public static boolean hasGerman(String template) {
        return GERMAN_TEXTS.containsKey(template);
    }

    static Map<String, String> germanTexts() {
        return GERMAN_TEXTS;
    }

    static String render(Text text, Locale requested) {
        Locale locale = supported(requested);
        String template = switch (locale.getLanguage()) {
            case "de" -> GERMAN_TEXTS.getOrDefault(text.template(), text.template());
            case "qps" -> pseudo(text.template());
            default -> text.template();
        };
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = text.args().get(m.group(1));
            String replacement = value == null ? m.group() : format(value, locale);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String format(Object value, Locale locale) {
        if (value instanceof Text t) {
            return render(t, locale);
        }
        if (value instanceof Map<?, ?> m && m.containsKey("template")) {
            // A nested text that went through JSON (e.g. a republished event).
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) m;
            return render(fromMap(json), locale);
        }
        if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
            NumberFormat nf = NumberFormat.getNumberInstance(locale.getLanguage().equals("en") ? Locale.ENGLISH : GERMAN);
            nf.setGroupingUsed(false);
            nf.setMaximumFractionDigits(2);
            if (value instanceof BigDecimal bd) {
                // Amounts keep their decimals: 380.00 -> "380.00" / "380,00".
                nf.setMinimumFractionDigits(Math.min(Math.max(bd.scale(), 0), 2));
            }
            return nf.format(value);
        }
        return String.valueOf(value);
    }

    /** Accents the letters outside placeholders and brackets the whole template. */
    static String pseudo(String template) {
        StringBuilder out = new StringBuilder("⟦");
        boolean inPlaceholder = false;
        for (char c : template.toCharArray()) {
            if (c == '{') {
                inPlaceholder = true;
            } else if (c == '}') {
                inPlaceholder = false;
            }
            out.append(inPlaceholder ? c : ACCENTS.getOrDefault(c, c));
        }
        return out.append('⟧').toString();
    }

    // ------------------------------------------------------------ storage as JSON

    /** JSON-friendly form for storing a text (nested texts become maps with "template"). */
    public static Map<String, Object> toMap(Text text) {
        Map<String, Object> args = new LinkedHashMap<>();
        text.args().forEach((k, v) -> args.put(k, v instanceof Text t ? toMap(t) : v));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("template", text.template());
        map.put("args", args);
        return map;
    }

    /** A stored text: the JSON form if present, else the English column (rows from before ADR-020). */
    public static Text stored(Map<String, Object> json, String english) {
        if (json != null) {
            return fromMap(json);
        }
        return english == null ? null : Text.of(english);
    }

    /** JSON form of a text that may be absent. */
    public static Map<String, Object> toMapOrNull(Text text) {
        return text == null ? null : toMap(text);
    }

    @SuppressWarnings("unchecked")
    public static Text fromMap(Map<String, Object> map) {
        Map<String, Object> args = new LinkedHashMap<>();
        Object stored = map.get("args");
        if (stored instanceof Map<?, ?> m) {
            m.forEach((k, v) -> args.put((String) k, v instanceof Map<?, ?> nested
                    ? fromMap((Map<String, Object>) nested) : v));
        }
        return new Text((String) map.get("template"), args);
    }

    private static Map<String, String> load(String resource) {
        try (InputStream in = Translator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Map.of();
            }
            return Map.copyOf(JSON.readValue(in, new TypeReference<Map<String, String>>() { }));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
