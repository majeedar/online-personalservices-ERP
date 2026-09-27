package edu.university.ops.shared.i18n;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A user-facing text as an English template plus named values (ADR-020), e.g.
 * {@code Text.of("Your {type} request for {period} was submitted.", "type", leaveType, "period", period)}.
 * It is rendered in the reader's language when shown, so a stored notification can be
 * read in German by one person and in English by another.
 *
 * <p>Values are kept as strings, numbers or nested {@link Text}s (a nested text is
 * translated too, e.g. a leave-type name). Dates are fixed to {@code dd.MM.yyyy},
 * which reads the same in both languages.
 */
public record Text(String template, Map<String, Object> args) {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Text {
        args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    /** A fixed text; the template is also the key into the German dictionary. */
    public static Text of(String template) {
        return new Text(template, Map.of());
    }

    /** A template with values given as name/value pairs. */
    public static Text of(String template, Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Values must be given as name/value pairs");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            args.put((String) namesAndValues[i], normalise(namesAndValues[i + 1]));
        }
        return new Text(template, args);
    }

    /** "a, b, c" where every item is translated. */
    public static Text list(java.util.List<Text> items) {
        StringBuilder template = new StringBuilder();
        Object[] namesAndValues = new Object[items.size() * 2];
        for (int i = 0; i < items.size(); i++) {
            template.append(i == 0 ? "" : ", ").append("{i").append(i).append('}');
            namesAndValues[2 * i] = "i" + i;
            namesAndValues[2 * i + 1] = items.get(i);
        }
        return of(template.toString(), namesAndValues);
    }

    private static Object normalise(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DATE);
        }
        if (value instanceof Text || value instanceof Number || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    /** Rendered in the language of the current request (English outside a request). */
    public String render() {
        return Translator.render(this, Translator.currentLocale());
    }

    public String render(Locale locale) {
        return Translator.render(this, locale);
    }

    /** English text, for logs, audit and stored fallbacks. */
    public String english() {
        return Translator.render(this, Locale.ENGLISH);
    }

    @Override
    public String toString() {
        return english();
    }
}
