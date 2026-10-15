package ru.eltex.producthub.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Доступ по ролям (SPEC.md раздел 6). CSRF включен. Прав на уровне сервисов пока нет, их добавляют
 * этапы с бизнес-логикой (@PreAuthorize, включено через @EnableMethodSecurity).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, Environment env, LdapProperties ldap,
                                    @org.springframework.beans.factory.annotation.Qualifier("ldapAuthenticationManager")
                                    org.springframework.beans.factory.ObjectProvider<org.springframework.security.authentication.AuthenticationManager> ldapManager)
            throws Exception {
        boolean dev = env.acceptsProfiles(Profiles.of("dev"));
        AuthenticationEntryPoint entryPoint = ldap.enabled()
                ? new LoginUrlAuthenticationEntryPoint("/login")
                : dev ? new LoginUrlAuthenticationEntryPoint("/dev/login")
                : new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        if (ldap.enabled()) {
            // Вход по логину и паролю из AD/LDAP: пароль проверяет каталог, HUB его не хранит
            http.authenticationManager(ldapManager.getObject())
                    .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
                    .logout(l -> l.logoutSuccessUrl("/login?logout"));
        }

        // Встраивать наши страницы можно только в наши же (страница материала показывает PDF во встроенном окне)
        http.headers(h -> h.frameOptions(f -> f.sameOrigin()));
        http.authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/css/**", "/vendor/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        // Проверка состояния для мониторинга и Docker: без входа, детали не показываются
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/login").permitAll()
                        // Контроллер /dev существует только в профиле dev, в prod здесь будет 404.
                        .requestMatchers("/dev/**").permitAll()
                        .requestMatchers("/feedback", "/feedback/export", "/feedback/comments/*/status").hasAnyRole("PRODUCT", "ADMIN")
                        .requestMatchers("/dashboard/**").hasAnyRole("PRODUCT", "HEAD", "ADMIN")
                        .requestMatchers("/directories/import", "/directories/daily-checks", "/directories/reindex").hasRole("ADMIN")
                        .requestMatchers("/directories/**").hasAnyRole("PRODUCT", "ADMIN")
                        .anyRequest().hasAnyRole(Role.INTERNAL))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .csrf(c -> {
                });
        return http.build();
    }
}
