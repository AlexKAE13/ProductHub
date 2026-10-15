package ru.eltex.producthub.importer;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.artifact.service.ArtifactPermissions;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.tag.domain.TagCategory;
import ru.eltex.producthub.tag.service.TagService;

/** Справочники (только просмотр на этапе 1) и запуск импорта (ADMIN). */
@Controller
public class DirectoriesController {

    private final ReferenceDataService reference;
    private final SeedImportService importer;
    private final SkuPermissions permissions;
    private final TagService tagService;
    private final ArtifactTypeService typeService;
    private final ArtifactPermissions artifactPermissions;
    private final FileImportProperties fileProps;
    private final ru.eltex.producthub.artifact.service.ArtifactStalenessJob stalenessJob;
    private final ru.eltex.producthub.sku.service.SkuReviewJob reviewJob;

    public DirectoriesController(ReferenceDataService reference, SeedImportService importer,
                                 SkuPermissions permissions, TagService tagService, ArtifactTypeService typeService,
                                 ArtifactPermissions artifactPermissions, FileImportProperties fileProps,
                                 ru.eltex.producthub.artifact.service.ArtifactStalenessJob stalenessJob,
                                 ru.eltex.producthub.sku.service.SkuReviewJob reviewJob) {
        this.reference = reference;
        this.importer = importer;
        this.permissions = permissions;
        this.tagService = tagService;
        this.typeService = typeService;
        this.artifactPermissions = artifactPermissions;
        this.fileProps = fileProps;
        this.stalenessJob = stalenessJob;
        this.reviewJob = reviewJob;
    }

    @GetMapping("/directories")
    public String index(@AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("activeMenu", "directories");
        model.addAttribute("lines", reference.lines());
        model.addAttribute("specs", reference.specDefinitions());
        model.addAttribute("canImport", permissions.canImport(user));
        model.addAttribute("runs", importer.latestRuns(10));
        model.addAttribute("tags", tagService.all());
        model.addAttribute("tagUsage", tagService.usageByTag());
        model.addAttribute("tagCategories", TagCategory.values());
        model.addAttribute("artifactTypes", typeService.all());
        model.addAttribute("fileRules", fileProps);
        model.addAttribute("canManage", artifactPermissions.canManage(user));
        return "directories/index";
    }

    @PostMapping("/directories/import")
    public String runImport(@AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        ImportRun run = importer.run(user);
        redirect.addFlashAttribute("lastRun", run);
        return "redirect:/directories";
    }

    @PostMapping("/directories/tags")
    public String createTag(@RequestParam TagCategory category, @RequestParam String value,
                            @RequestParam(required = false) String valueEn,
                            @RequestParam(required = false) String aliases,
                            @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        try {
            tagService.create(category, value, valueEn, aliases, user);
        } catch (ru.eltex.producthub.tag.service.TagDuplicateException e) {
            redirect.addFlashAttribute("tagError", e.getErrors().values().iterator().next());
            redirect.addFlashAttribute("tagConflict", e.getExisting().getCategory() + ": " + e.getExisting().getValue());
            redirect.addFlashAttribute("tagConflictCategory", e.getExisting().getCategory().name());
            redirect.addFlashAttribute("tagConflictValue", e.getExisting().getValue());
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("tagError", e.getErrors().values().iterator().next());
        }
        return "redirect:/directories#tags";
    }

    @PostMapping("/directories/tags/{id}/active")
    public String setTagActive(@PathVariable Long id, @RequestParam boolean active,
                               @AuthenticationPrincipal HubUserDetails user) {
        tagService.setActive(id, active, user);
        return "redirect:/directories#tags";
    }

    @PostMapping("/directories/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        var usage = tagService.delete(id, user);
        redirect.addFlashAttribute("tagDeleted", usage.total());
        return "redirect:/directories#tags";
    }

    /** Ручной запуск ежедневных проверок (обычно они идут сами в 07:00). Только ADMIN. */
    @PostMapping("/directories/daily-checks")
    public String dailyChecks(@AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        if (!permissions.canImport(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Только администратор");
        }
        redirect.addFlashAttribute("staleArtifacts", stalenessJob.run());
        redirect.addFlashAttribute("reviewSkus", reviewJob.run());
        return "redirect:/directories#checks";
    }
}
