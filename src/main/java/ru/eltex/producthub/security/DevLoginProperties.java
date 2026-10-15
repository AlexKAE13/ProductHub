package ru.eltex.producthub.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Настройки dev-входа. hidden - логины, которых нет в списке выбора (например тестовые, когда приложение открыто по сети). */
@ConfigurationProperties(prefix = "producthub.dev-login")
public record DevLoginProperties(@DefaultValue List<String> hidden) {

    public boolean isHidden(String login) {
        return hidden != null && hidden.contains(login);
    }
}
