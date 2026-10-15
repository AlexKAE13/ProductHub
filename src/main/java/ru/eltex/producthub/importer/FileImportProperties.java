package ru.eltex.producthub.importer;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Правила разбора имен файлов (SPEC.md 5.11). Лежат в конфигурации (application.yml), а не в коде.
 * Именованные группы regexp: competitor, segment, topic, market превращаются в теги.
 */
@ConfigurationProperties(prefix = "producthub.import.files")
public record FileImportProperties(
        @DefaultValue List<String> ignore,
        @DefaultValue("(?i)[ _-](?<lang>RU_EN|RU|EN)(?:[ _]\\d+)?$") String languagePattern,
        @DefaultValue List<Rule> rules,
        @DefaultValue("Карточка продукта") String productType,
        @DefaultValue("Прочее") String otherType,
        @DefaultValue({"pptx", "docx", "xlsx"}) List<String> mainPriority) {

    /** Если имя (без языка и расширения) подходит под pattern, файл получает тип и контур. */
    public record Rule(String pattern, String type, String contour) {
    }
}
