package ru.eltex.producthub.sku.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.service.CatalogFilter;
import ru.eltex.producthub.sku.service.CatalogService;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuPermissions;

@Controller
public class CatalogController {

    private final CatalogService catalog;
    private final ReferenceDataService reference;
    private final SkuPermissions permissions;
    private final ru.eltex.producthub.settings.UserSettingsService settings;
    private final ru.eltex.producthub.tag.service.TagService tagService;
    private final ru.eltex.producthub.firmware.service.FirmwareService firmwareService;

    public CatalogController(CatalogService catalog, ReferenceDataService reference, SkuPermissions permissions,
                             ru.eltex.producthub.settings.UserSettingsService settings,
                             ru.eltex.producthub.tag.service.TagService tagService,
                             ru.eltex.producthub.firmware.service.FirmwareService firmwareService) {
        this.catalog = catalog;
        this.reference = reference;
        this.permissions = permissions;
        this.settings = settings;
        this.tagService = tagService;
        this.firmwareService = firmwareService;
    }

    @GetMapping("/catalog")
    public String catalog(@RequestParam(required = false) String family,
                          @RequestParam(required = false) String line,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) Long owner,
                          @RequestParam(required = false) Long tag,
                          @RequestParam(defaultValue = "false") boolean reviewDue,
                          @RequestParam(defaultValue = "false") boolean includeEol,
                          @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "model") String sort,
                          @RequestParam(defaultValue = "asc") String dir,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          jakarta.servlet.http.HttpServletRequest request,
                          @AuthenticationPrincipal HubUserDetails user,
                          Model model) {
        // Первое открытие без параметров: берем личные настройки каталога
        if (request.getQueryString() == null && htmx == null) {
            var cfg = settings.get(user.getId());
            if (cfg.catalogOnlyMine() && user.getRoles().contains(ru.eltex.producthub.security.Role.PRODUCT)) {
                owner = user.getId();
            }
            includeEol = includeEol || cfg.catalogIncludeEol();
        }
        CatalogFilter filter = new CatalogFilter(family, line, category, status, owner, tag, reviewDue, includeEol, q,
                sort, dir, page);
        var result = catalog.search(filter, user);
        model.addAttribute("page", result);
        model.addAttribute("currentFirmware", firmwareService.currentFor(
                result.items().stream().map(ru.eltex.producthub.sku.domain.Sku::getId).toList()));
        model.addAttribute("filter", filter);
        model.addAttribute("activeMenu", "catalog");
        model.addAttribute("canCreate", permissions.canCreate(user));
        model.addAttribute("today", java.time.LocalDate.now());
        if (htmx != null) {
            return "catalog/index :: results";
        }
        model.addAttribute("families", reference.families());
        model.addAttribute("lines", reference.lines());
        model.addAttribute("categories", reference.categories());
        model.addAttribute("owners", reference.owners());
        model.addAttribute("allTags", tagService.active());
        model.addAttribute("statuses", LifecycleStatus.values());
        return "catalog/index";
    }
}
