package ru.eltex.producthub.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;

/** Превращает запись из каталога в пользователя HUB: имя и почта из атрибутов, роли по группам. Без групп HUB вход закрыт. */
public class LdapUserMapper implements UserDetailsContextMapper {

    private final LdapProperties props;
    private final LdapUserProvisioner provisioner;

    public LdapUserMapper(LdapProperties props, LdapUserProvisioner provisioner) {
        this.props = props;
        this.provisioner = provisioner;
    }

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority a : authorities) {
            roles.addAll(props.rolesFor(a.getAuthority()));
        }
        if (roles.isEmpty()) {
            throw new DisabledException("Пользователь не состоит ни в одной группе Product HUB");
        }
        String login = ctx.getStringAttribute(props.loginAttribute());
        if (login == null || login.isBlank()) {
            login = username;
        }
        return provisioner.sync(login.trim().toLowerCase(Locale.ROOT), ctx.getStringAttribute(props.nameAttribute()),
                ctx.getStringAttribute(props.mailAttribute()), roles);
    }

    @Override
    public void mapUserToContext(UserDetails user, org.springframework.ldap.core.DirContextAdapter ctx) {
        throw new UnsupportedOperationException("Изменение каталога из HUB не поддерживается");
    }
}
