package ru.eltex.producthub.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Переключатель "войти как". Существует только в профиле dev. */
@Controller
@Profile("dev")
@RequestMapping("/dev")
public class DevLoginController {

    private final HubUserDetailsService userService;
    private final ru.eltex.producthub.settings.UserSettingsService settings;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    private final DevLoginProperties props;

    public DevLoginController(HubUserDetailsService userService, ru.eltex.producthub.settings.UserSettingsService settings,
                              DevLoginProperties props) {
        this.props = props;
        this.userService = userService;
        this.settings = settings;
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("users", userService.activeUsers().stream().filter(u -> !props.isHidden(u.getUsername())).toList());
        return "dev/login";
    }

    @PostMapping("/login-as")
    public String loginAs(@RequestParam String login, HttpServletRequest request, HttpServletResponse response) {
        HubUserDetails user = userService.loadUserByUsername(login);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        contextRepository.saveContext(context, request, response);
        // стартовая страница из личных настроек
        return "redirect:" + settings.get(user.getId()).startPage().getPath();
    }
}
