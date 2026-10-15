package ru.eltex.producthub.feedback.service;

import java.util.Set;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;

/** Права на комментарии: писать могут все внутренние роли, разбирать и выгружать - продакты и админ. */
@Component
public class FeedbackPermissions {

    private static final Set<Role> WRITERS = Set.of(Role.PRODUCT, Role.SERVICE_CENTER, Role.SALES, Role.RND, Role.HEAD, Role.ADMIN);
    private static final Set<Role> MANAGERS = Set.of(Role.PRODUCT, Role.ADMIN);

    public boolean canComment(HubUserDetails user) {
        return user.getRoles().stream().anyMatch(WRITERS::contains);
    }

    public boolean canManage(HubUserDetails user) {
        return user.getRoles().stream().anyMatch(MANAGERS::contains);
    }
}
