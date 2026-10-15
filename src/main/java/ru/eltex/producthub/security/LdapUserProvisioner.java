package ru.eltex.producthub.security;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** При входе через LDAP заводит пользователя в базе HUB (если его еще нет) и приводит роли в соответствие с группами каталога. */
@Service
public class LdapUserProvisioner {

    private final UserRepository users;

    public LdapUserProvisioner(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public HubUserDetails sync(String login, String fullName, String email, Set<Role> roles) {
        User user = users.findByLogin(login).orElseGet(() -> new User(login, fullName, email));
        user.setActive(true);
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(email);
        }
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        return new HubUserDetails(users.save(user));
    }
}
