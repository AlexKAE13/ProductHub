package ru.eltex.producthub.importer;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Правила импорта из application.yml (SPEC.md 5.11): правила разбора лежат в конфигурации, а не в коде. */
@ConfigurationProperties(prefix = "producthub.import")
public record ImportProperties(
        @DefaultValue("seed") String seedDir,
        @DefaultValue("true") boolean onFirstStart,
        @DefaultValue("kuznetsov") String defaultOwner,
        @DefaultValue("seed") String folderRoots,
        @DefaultValue("Прочее") String defaultFamily,
        @DefaultValue("OTHER") String otherLine,
        @DefaultValue List<Rule> ownerAliases,
        @DefaultValue List<Rule> familyBySection,
        @DefaultValue List<Rule> categoryByLine,
        @DefaultValue List<Rule> categoryByPrefix,
        @DefaultValue List<String> skipTypes,
        @DefaultValue List<String> extraTypes,
        @DefaultValue List<Rule> cardBlockByType) {

    /** Пара "что искать - что подставить". */
    public record Rule(String match, String value) {
    }

    public String find(List<Rule> rules, String key) {
        if (key == null) {
            return null;
        }
        for (Rule r : rules) {
            if (r.match().equalsIgnoreCase(key.trim())) {
                return r.value();
            }
        }
        return null;
    }
}
