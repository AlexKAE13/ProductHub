package ru.eltex.producthub.sku.web;

import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.domain.Availability;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.PriceList;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuBlock;
import ru.eltex.producthub.sku.service.SkuCard;
import ru.eltex.producthub.sku.service.SkuForm;
import ru.eltex.producthub.sku.service.SkuNotFoundException;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.sku.service.SkuValidationException;

/**
 * Карточка SKU. Адрес карточки содержит артикул (бывает с "/", например PM300T-220/12), поэтому он последний
 * в пути. Все действия идут по числовому id: /skus/{id}/...
 */
@Controller
public class SkuController {

    private final SkuService service;
    private final ReferenceDataService reference;
    private final SkuPermissions permissions;
    private final ru.eltex.producthub.artifact.service.SkuMaterialsService materials;
    private final ru.eltex.producthub.tag.service.TagService tagService;
    private final ru.eltex.producthub.firmware.service.FirmwareService firmwareService;
    private final ru.eltex.producthub.casestudy.service.CaseService caseService;
    private final ru.eltex.producthub.qa.service.QuestionService questionService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    public SkuController(SkuService service, ReferenceDataService reference, SkuPermissions permissions,
                         ru.eltex.producthub.artifact.service.SkuMaterialsService materials,
                         ru.eltex.producthub.tag.service.TagService tagService,
                         ru.eltex.producthub.firmware.service.FirmwareService firmwareService,
                         ru.eltex.producthub.casestudy.service.CaseService caseService,
                         ru.eltex.producthub.qa.service.QuestionService questionService) {
        this.service = service;
        this.reference = reference;
        this.permissions = permissions;
        this.materials = materials;
        this.tagService = tagService;
        this.firmwareService = firmwareService;
        this.caseService = caseService;
        this.questionService = questionService;
    }

    // ------------------------------------------------------------ карточка

    @GetMapping("/sku/{*model}")
    public String card(@PathVariable String model, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        String artikul = model.startsWith("/") ? model.substring(1) : model;
        SkuCard card = service.loadCard(artikul, user);
        events.publishEvent(new ru.eltex.producthub.common.ObjectViewed("SKU", card.sku().getId(), user.getUsername()));
        fillCard(ui, card);
        ui.addAttribute("materials", materials.forSku(card.sku().getId(), card.sku().getLine().getId(), "ALL", user));
        ui.addAttribute("fw", firmwareService.forSku(card.sku().getId(), user));
        ui.addAttribute("cs", caseService.forSku(card.sku().getId(), user));
        ui.addAttribute("qa", questionService.view(ru.eltex.producthub.qa.domain.TargetType.SKU, card.sku().getId(), user));
        ui.addAttribute("activeMenu", "catalog");
        ui.addAttribute("today", java.time.LocalDate.now());
        return "sku/card";
    }

    private void fillCard(Model ui, SkuCard card) {
        ui.addAttribute("card", card);
        ui.addAttribute("s", card.sku());
    }

    // ------------------------------------------------------------ материалы (блоки 5, 6, 9)

    /** Фильтр языка одним кликом: ALL, RU, EN. */
    @GetMapping("/skus/{id}/materials")
    public String materialsBlock(@PathVariable Long id, @RequestParam(defaultValue = "ALL") String lang,
                                 @AuthenticationPrincipal HubUserDetails user, Model ui) {
        SkuCard card = service.loadCard(id, user);
        fillCard(ui, card);
        ui.addAttribute("materials", materials.forSku(id, card.sku().getLine().getId(), lang, user));
        return "sku/materials :: docs-compare";
    }

    // ------------------------------------------------------------ блоки (HTMX)

    @GetMapping("/skus/{id}/block/{block}")
    public String viewBlock(@PathVariable Long id, @PathVariable String block,
                            @AuthenticationPrincipal HubUserDetails user, Model ui) {
        fillCard(ui, service.loadCard(id, user));
        ui.addAttribute("today", java.time.LocalDate.now());
        return "sku/card :: block-" + SkuBlock.parse(block).name().toLowerCase();
    }

    @GetMapping("/skus/{id}/edit/{block}")
    public String editBlock(@PathVariable Long id, @PathVariable String block,
                            @AuthenticationPrincipal HubUserDetails user, Model ui) {
        SkuBlock b = SkuBlock.parse(block);
        fillCard(ui, service.loadCard(id, user));
        ui.addAttribute("form", service.formFor(id, user));
        fillEditModel(ui, id, b, user);
        return "sku/edit :: edit-" + b.name().toLowerCase();
    }

    @PostMapping("/skus/{id}/edit/{block}")
    public String saveBlock(@PathVariable Long id, @PathVariable String block, @ModelAttribute("form") SkuForm form,
                            @AuthenticationPrincipal HubUserDetails user, Model ui) {
        SkuBlock b = SkuBlock.parse(block);
        try {
            service.update(id, b, form, user);
        } catch (SkuValidationException e) {
            fillCard(ui, service.loadCard(id, user));
            fillEditModel(ui, id, b, user);
            ui.addAttribute("errors", e.getErrors());
            return "sku/edit :: edit-" + b.name().toLowerCase();
        }
        fillCard(ui, service.loadCard(id, user));
        ui.addAttribute("today", java.time.LocalDate.now());
        return "sku/card :: saved-" + b.name().toLowerCase();
    }

    private void fillEditModel(Model ui, Long id, SkuBlock block, HubUserDetails user) {
        ui.addAttribute("block", block.name().toLowerCase());
        ui.addAttribute("lines", reference.lines());
        ui.addAttribute("owners", reference.owners());
        ui.addAttribute("allTags", tagService.active());
        ui.addAttribute("categories", reference.categories());
        ui.addAttribute("statuses", LifecycleStatus.values());
        ui.addAttribute("contours", Contour.values());
        ui.addAttribute("priceLists", PriceList.values());
        ui.addAttribute("availabilities", Availability.values());
        if (block == SkuBlock.SPECS) {
            var grouped = new java.util.LinkedHashMap<String, java.util.List<ru.eltex.producthub.sku.domain.SpecDefinition>>();
            service.editableSpecs(id, user).forEach(d -> grouped.computeIfAbsent(d.getGroup(), k -> new java.util.ArrayList<>()).add(d));
            ui.addAttribute("editableSpecGroups", grouped);
        }
    }

    @PostMapping("/skus/{id}/review")
    public String review(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        service.markReviewed(id, user);
        fillCard(ui, service.loadCard(id, user));
        ui.addAttribute("today", java.time.LocalDate.now());
        return "sku/card :: saved-lifecycle";
    }

    @PostMapping("/skus/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        service.delete(id, user);
        return "redirect:/catalog";
    }

    // ------------------------------------------------------------ создание

    @GetMapping("/skus/new")
    public String newForm(@RequestParam(required = false) Long copyFrom,
                          @AuthenticationPrincipal HubUserDetails user, Model ui) {
        if (!permissions.canCreate(user)) {
            throw new AccessDeniedException("Нет прав на создание SKU");
        }
        SkuForm form = new SkuForm();
        if (copyFrom != null) {
            SkuForm source = service.formFor(copyFrom, user);
            form.setLineId(source.getLineId());
            form.setCategory(source.getCategory());
            form.setOwnerId(source.getOwnerId());
            form.setChip(source.getChip());
            form.setLifecycleStatus(LifecycleStatus.DEVELOPMENT.name());
            form.setSiteSectionRu(source.getSiteSectionRu());
            form.setSiteSubsectionRu(source.getSiteSubsectionRu());
            form.setSiteSectionEn(source.getSiteSectionEn());
            form.setSiteSubsectionEn(source.getSiteSubsectionEn());
            form.setCopyFromId(copyFrom);
            ui.addAttribute("copyModel", source.getModel());
        } else {
            form.setLifecycleStatus(LifecycleStatus.DEVELOPMENT.name());
            form.setOwnerId(user.getId());
        }
        fillCreateModel(ui, form);
        return "sku/new";
    }

    @PostMapping("/skus")
    public String create(@ModelAttribute("form") SkuForm form, @AuthenticationPrincipal HubUserDetails user,
                         Model ui) {
        try {
            var sku = service.create(form, user);
            return "redirect:/sku/" + sku.getModel().replace(" ", "%20");
        } catch (SkuValidationException e) {
            ui.addAttribute("errors", e.getErrors());
            fillCreateModel(ui, form);
            return "sku/new";
        }
    }

    private void fillCreateModel(Model ui, SkuForm form) {
        ui.addAttribute("form", form);
        ui.addAttribute("lines", reference.lines());
        ui.addAttribute("owners", reference.owners());
        ui.addAttribute("allTags", tagService.active());
        ui.addAttribute("categories", reference.categories());
        ui.addAttribute("statuses", LifecycleStatus.values());
        ui.addAttribute("activeMenu", "catalog");
        ui.addAttribute("errors", ui.getAttribute("errors") == null ? Map.of() : ui.getAttribute("errors"));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SkuNotFoundException.class)
    public void notFound(SkuNotFoundException e, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        response.sendError(404);
    }
}
