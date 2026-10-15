package ru.eltex.producthub.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница входа по логину и паролю (только при включенном LDAP). */
@Controller
@ConditionalOnProperty(name = "producthub.ldap.enabled", havingValue = "true")
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }
}
