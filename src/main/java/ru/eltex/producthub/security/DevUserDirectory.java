package ru.eltex.producthub.security;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Список пользователей для переключателя в шапке (шаблон layout.html). Только dev. */
@Component("devUsers")
@Profile("dev")
public class DevUserDirectory {

    private final HubUserDetailsService userService;

    private final DevLoginProperties props;

    public DevUserDirectory(HubUserDetailsService userService, DevLoginProperties props) {
        this.props = props;
        this.userService = userService;
    }

    public List<HubUserDetails> all() {
        return userService.activeUsers().stream().filter(u -> !props.isHidden(u.getUsername())).toList();
    }
}
