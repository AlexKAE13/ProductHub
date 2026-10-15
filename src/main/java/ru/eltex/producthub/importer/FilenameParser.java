package ru.eltex.producthub.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.storage.FileTypes;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/** Разбор имени файла по правилам из конфигурации. Чистая логика без обращения к БД. */
@Component
public class FilenameParser {

    private static final List<String> TAG_GROUPS = List.of("competitor", "segment", "topic", "market");

    private final Pattern languagePattern;
    private final List<CompiledRule> rules;
    private final List<Pattern> ignore;

    public FilenameParser(FileImportProperties props) {
        this.languagePattern = Pattern.compile(props.languagePattern());
        this.rules = props.rules().stream()
                .map(r -> new CompiledRule(Pattern.compile(r.pattern()), r.type(), r.contour())).toList();
        this.ignore = props.ignore().stream().map(FilenameParser::glob).toList();
    }

    /** Результат разбора имени файла. */
    public record Parsed(String fileName, String ext, String stem, String body, Language language, String key) {
    }

    /** Совпавшее правило типа: имя типа, контур (может быть null) и значения именованных групп. */
    public record Match(String type, String contour, Map<String, String> groups) {
    }

    private record CompiledRule(Pattern pattern, String type, String contour) {
    }

    public boolean isIgnored(String fileName) {
        return ignore.stream().anyMatch(p -> p.matcher(fileName).matches());
    }

    public Parsed parse(String fileName) {
        String ext = FileTypes.extension(fileName);
        String stem = ext.isEmpty() ? fileName : fileName.substring(0, fileName.length() - ext.length() - 1);
        Language language = Language.NONE;
        String body = stem;
        Matcher m = languagePattern.matcher(stem);
        if (m.find()) {
            language = Language.valueOf(m.group("lang").toUpperCase(Locale.ROOT));
            body = stem.substring(0, m.start());
        }
        body = body.trim();
        // ключ группы: pptx и pdf с одинаковым именем (в разных папках, с разным регистром и пробелами) - один материал
        String key = body.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "_") + "|" + language.name();
        return new Parsed(fileName, ext, stem, body, language, key);
    }

    public Match match(String body) {
        for (CompiledRule r : rules) {
            Matcher m = r.pattern().matcher(body);
            if (m.find()) {
                Map<String, String> groups = new LinkedHashMap<>();
                for (String g : TAG_GROUPS) {
                    try {
                        String v = m.group(g);
                        if (v != null) {
                            groups.put(g, v);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // в этом правиле нет такой группы
                    }
                }
                return new Match(r.type(), r.contour(), groups);
            }
        }
        return null;
    }

    /** Заголовок материала из имени: подчеркивания в пробелы. */
    public String title(String body) {
        return body.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    public static String normalizeCode(String text) {
        return ModelNormalizer.normalize(text);
    }

    private static Pattern glob(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
}
