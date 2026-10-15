package ru.eltex.producthub.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

/** Проверка пароля в AD или LDAP (включается настройкой producthub.ldap.enabled=true). Пароли HUB не хранит. */
@Configuration
@ConditionalOnProperty(name = "producthub.ldap.enabled", havingValue = "true")
public class LdapSecurityConfig {

    @Bean
    DefaultSpringSecurityContextSource ldapContextSource(LdapProperties props) {
        DefaultSpringSecurityContextSource source = new DefaultSpringSecurityContextSource(
                props.base() == null || props.base().isBlank() ? props.url() : props.url() + "/" + props.base());
        if (props.managerDn() != null && !props.managerDn().isBlank()) {
            source.setUserDn(props.managerDn());
            source.setPassword(props.managerPassword());
        }
        source.afterPropertiesSet();
        return source;
    }

    @Bean
    AuthenticationManager ldapAuthenticationManager(DefaultSpringSecurityContextSource source, LdapProperties props, LdapUserProvisioner provisioner) {
        BindAuthenticator authenticator = new BindAuthenticator(source);
        authenticator.setUserSearch(new FilterBasedLdapUserSearch(props.userSearchBase(), props.userSearchFilter(), source));
        DefaultLdapAuthoritiesPopulator populator = new DefaultLdapAuthoritiesPopulator(source, props.groupSearchBase());
        populator.setGroupSearchFilter(props.groupSearchFilter());
        populator.setRolePrefix("");
        populator.setConvertToUpperCase(false);
        populator.setSearchSubtree(true);
        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(authenticator, populator);
        provider.setUserDetailsContextMapper(new LdapUserMapper(props, provisioner));
        return new ProviderManager(provider);
    }
}
