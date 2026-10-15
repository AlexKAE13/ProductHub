package ru.eltex.producthub.security;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Вход через AD или LDAP (SPEC.md этап 7). Все значения задает ИТ в настройках (переменные окружения вида
 * PRODUCTHUB_LDAP_URL). groupRoles сопоставляет имя (cn) группы каталога с ролями HUB.
 */
@ConfigurationProperties(prefix = "producthub.ldap")
public record LdapProperties(
        @DefaultValue("false") boolean enabled,
        String url,
        String base,
        String managerDn,
        String managerPassword,
        @DefaultValue("") String userSearchBase,
        @DefaultValue("(uid={0})") String userSearchFilter,
        @DefaultValue("uid") String loginAttribute,
        @DefaultValue("cn") String nameAttribute,
        @DefaultValue("mail") String mailAttribute,
        @DefaultValue("") String groupSearchBase,
        @DefaultValue("(member={0})") String groupSearchFilter,
        @DefaultValue Map<String, List<Role>> groupRoles) {

    /** Роли по именам групп без учета регистра. */
    public List<Role> rolesFor(String group) {
        return groupRoles.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(group)).findFirst()
                .map(Map.Entry::getValue).orElse(List.of());
    }
}
