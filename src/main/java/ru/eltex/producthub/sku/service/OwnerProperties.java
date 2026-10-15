package ru.eltex.producthub.sku.service;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Совладельцы SKU по семействам продуктов (producthub.owners.families в application.yml). */
@ConfigurationProperties(prefix = "producthub.owners")
public record OwnerProperties(@DefaultValue List<Group> families) {

    /** Семейство и логины его совладельцев. Порядок важен: по кругу из них выбирается основной владелец линейки. */
    public record Group(String family, List<String> logins) {
    }

    public List<String> loginsFor(String family) {
        return families.stream().filter(g -> g.family().equalsIgnoreCase(family)).findFirst().map(Group::logins).orElse(List.of());
    }
}
