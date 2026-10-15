package ru.eltex.producthub.security;

/** Роли из SPEC.md, раздел 2. */
public enum Role {
    PRODUCT,
    SERVICE_CENTER,
    SALES,
    RND,
    HEAD,
    ADMIN,
    /** Внешний контур, в v0 не используется, но объявлена сразу. */
    PARTNER;

    public static final String[] INTERNAL = {"PRODUCT", "SERVICE_CENTER", "SALES", "RND", "HEAD", "ADMIN"};
}
