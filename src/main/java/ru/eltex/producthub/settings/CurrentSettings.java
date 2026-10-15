package ru.eltex.producthub.settings;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import ru.eltex.producthub.security.HubUserDetails;

/** Настройки текущего пользователя для шаблонов: {@code ${@currentSettings.get()}}. */
@Component("currentSettings")
public class CurrentSettings {

    private final UserSettingsService service;

    public CurrentSettings(UserSettingsService service) {
        this.service = service;
    }

    public UserSettingsView get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HubUserDetails user) {
            return service.get(user.getId());
        }
        return UserSettingsView.DEFAULTS;
    }
}
