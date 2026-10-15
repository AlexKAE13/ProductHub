package ru.eltex.producthub.artifact.service;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.service.SkuPermissions;

/** Права на артефакты (SPEC.md раздел 6) и проверка контура (раздел 7). */
@Component
public class ArtifactPermissions {

    private final SkuPermissions skuPermissions;

    public ArtifactPermissions(SkuPermissions skuPermissions) {
        this.skuPermissions = skuPermissions;
    }

    private boolean has(HubUserDetails user, Role... roles) {
        for (Role r : roles) {
            if (user.getRoles().contains(r)) {
                return true;
            }
        }
        return false;
    }

    /** Загружать, править, подтверждать актуальность. */
    public boolean canEdit(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.SERVICE_CENTER, Role.ADMIN);
    }

    /** Массовые действия, импорт папки, создание тегов и правка справочников. */
    public boolean canManage(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN);
    }

    public boolean canView(HubUserDetails user, Artifact artifact) {
        return skuPermissions.isInternal(user) || artifact.getContour() == Contour.EXTERNAL;
    }

    public boolean isInternal(HubUserDetails user) {
        return skuPermissions.isInternal(user);
    }
}
