package ru.eltex.producthub.sku.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.repo.SkuQueryRepository;

@Service
public class CatalogService {

    private final SkuQueryRepository queries;
    private final SkuPermissions permissions;

    public CatalogService(SkuQueryRepository queries, SkuPermissions permissions) {
        this.queries = queries;
        this.permissions = permissions;
    }

    /** Внутренние пользователи видят весь каталог, остальные только SKU внешнего контура (SPEC.md раздел 7). */
    @Transactional(readOnly = true)
    public CatalogPage search(CatalogFilter filter, HubUserDetails user) {
        boolean internal = permissions.isInternal(user);
        if (!internal && user.getRoles().isEmpty()) {
            throw new AccessDeniedException("Нет доступа к каталогу");
        }
        return queries.search(filter, !internal);
    }
}
