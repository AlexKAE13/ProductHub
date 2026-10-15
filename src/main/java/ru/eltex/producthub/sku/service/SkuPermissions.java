package ru.eltex.producthub.sku.service;

import java.util.Set;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecDefinition;

/** Все правила доступа к SKU в одном месте (SPEC.md раздел 6, 7). Сервисы вызывают их перед любым действием. */
@Component
public class SkuPermissions {

    private static final Set<Role> INTERNAL = Set.of(Role.PRODUCT, Role.SERVICE_CENTER, Role.SALES, Role.RND,
            Role.HEAD, Role.ADMIN);

    public boolean isInternal(HubUserDetails user) {
        return user.getRoles().stream().anyMatch(INTERNAL::contains);
    }

    private boolean has(HubUserDetails user, Role... roles) {
        for (Role r : roles) {
            if (user.getRoles().contains(r)) {
                return true;
            }
        }
        return false;
    }

    /** Создавать и править SKU целиком (идентификация, ЖЦ, коммерция, описание, планы). */
    public boolean canEditSku(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN);
    }

    public boolean canCreate(HubUserDetails user) {
        return canEditSku(user);
    }

    public boolean canEditSpec(HubUserDetails user, SpecDefinition definition) {
        return canEditSku(user) || (has(user, Role.SERVICE_CENTER) && definition.getEditableBy() == Role.SERVICE_CENTER);
    }

    public boolean canEditAnySpec(HubUserDetails user) {
        return canEditSku(user) || has(user, Role.SERVICE_CENTER);
    }

    /** Пустые характеристики видит только тот, кто может их заполнить целиком (PRODUCT, ADMIN). */
    public boolean canSeeEmptySpecs(HubUserDetails user) {
        return canEditSku(user);
    }

    public boolean canImport(HubUserDetails user) {
        return has(user, Role.ADMIN);
    }

    /** Правка продактом чужого SKU допустима, но помечается в аудите. */
    public boolean isForeign(HubUserDetails user, Sku sku) {
        if (has(user, Role.ADMIN)) {
            return false;
        }
        boolean ownSku = sku.isOwnedBy(user.getId());
        boolean ownLine = sku.getLine().getOwner() != null && sku.getLine().getOwner().getId().equals(user.getId());
        return !(ownSku || ownLine);
    }

    /** Можно ли пользователю открыть SKU вообще (контур). */
    public boolean canView(HubUserDetails user, Sku sku) {
        return isInternal(user) || sku.getContour() == Contour.EXTERNAL;
    }

    public boolean canSee(CardSection section, HubUserDetails user, Sku sku) {
        if (isInternal(user)) {
            return true;
        }
        return section.isExternal() && sku.getContour() == Contour.EXTERNAL;
    }
}
