package ru.eltex.producthub.artifact.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactFilter;
import ru.eltex.producthub.artifact.service.ArtifactForm;
import ru.eltex.producthub.artifact.service.ArtifactListService;
import ru.eltex.producthub.artifact.service.ArtifactNotFoundException;
import ru.eltex.producthub.artifact.service.ArtifactPermissions;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.artifact.service.IncomingFile;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.repo.ProductLineRepository;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.tag.service.TagService;

@Controller
public class ArtifactController {

    private final ArtifactService service;
    private final ArtifactListService listService;
    private final ArtifactTypeService types;
    private final ReferenceDataService reference;
    private final TagService tags;
    private final ArtifactPermissions permissions;
    private final ProductLineRepository lines;
    private final ru.eltex.producthub.qa.service.QuestionService questionService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    public ArtifactController(ArtifactService service, ArtifactListService listService, ArtifactTypeService types,
                              ReferenceDataService reference, TagService tags, ArtifactPermissions permissions,
                              ProductLineRepository lines, ru.eltex.producthub.qa.service.QuestionService questionService) {
        this.service = service;
        this.listService = listService;
        this.types = types;
        this.reference = reference;
        this.tags = tags;
        this.permissions = permissions;
        this.lines = lines;
        this.questionService = questionService;
    }

    // ------------------------------------------------------------------ список

    @GetMapping("/artifacts")
    public String list(@RequestParam(required = false) Long type, @RequestParam(required = false) String language,
                       @RequestParam(required = false) String status, @RequestParam(required = false) Long owner,
                       @RequestParam(required = false) String sku, @RequestParam(required = false) Long line,
                       @RequestParam(required = false) String family,
                       @RequestParam(required = false) Long tag, @RequestParam(required = false) String contour,
                       @RequestParam(defaultValue = "false") boolean reviewDue,
                       @RequestParam(required = false) String q, @RequestParam(defaultValue = "updated") String sort,
                       @RequestParam(defaultValue = "desc") String dir, @RequestParam(defaultValue = "0") int page,
                       @RequestHeader(value = "HX-Request", required = false) String htmx,
                       @AuthenticationPrincipal HubUserDetails user, Model model) {
        ArtifactFilter filter = new ArtifactFilter(type, language, status, owner, sku, line, family, tag, contour, reviewDue, q,
                sort, dir, page);
        model.addAttribute("view", listService.search(filter, user));
        model.addAttribute("filter", filter);
        model.addAttribute("activeMenu", "artifacts");
        model.addAttribute("canEdit", permissions.canEdit(user));
        model.addAttribute("canManage", permissions.canManage(user));
        model.addAttribute("now", java.time.LocalDateTime.now());
        // нужны и во фрагменте результатов: выбор значения для массовых действий
        model.addAttribute("statuses", ArtifactStatus.values());
        model.addAttribute("allTags", tags.active());
        if (htmx != null) {
            return "artifacts/index :: results";
        }
        model.addAttribute("types", types.all());
        model.addAttribute("owners", reference.owners());
        model.addAttribute("lines", reference.lines());
        model.addAttribute("families", reference.families());
        model.addAttribute("allTags", tags.active());
        model.addAttribute("statuses", ArtifactStatus.values());
        model.addAttribute("languages", Language.values());
        model.addAttribute("contours", Contour.values());
        return "artifacts/index";
    }

    // ------------------------------------------------------------------ страница артефакта

    @GetMapping("/artifacts/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("d", service.detail(id, user));
        events.publishEvent(new ru.eltex.producthub.common.ObjectViewed("ARTIFACT", id, user.getUsername()));
        model.addAttribute("qa", questionService.view(ru.eltex.producthub.qa.domain.TargetType.ARTIFACT, id, user));
        model.addAttribute("activeMenu", "artifacts");
        return "artifacts/detail";
    }

    @PostMapping("/artifacts/{id}/versions")
    public String addVersion(@PathVariable Long id, @RequestParam("files") List<MultipartFile> uploads,
                             @RequestParam(required = false) String comment,
                             @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        List<IncomingFile> incoming = uploads.stream().filter(f -> !f.isEmpty()).map(f -> IncomingFile.of(f, FileRole.MAIN))
                .toList();
        // pdf рядом с pptx сохраняется как предпросмотр
        if (incoming.size() == 2) {
            incoming = markPreview(incoming);
        }
        try {
            var added = service.addVersion(id, incoming, comment, user);
            redirect.addFlashAttribute("message", "artifacts.version.added");
            redirect.addFlashAttribute("warnings", added.duplicateWarnings());
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("versionErrors", e.getErrors());
        }
        return "redirect:/artifacts/" + id;
    }

    private static List<IncomingFile> markPreview(List<IncomingFile> files) {
        IncomingFile pdf = files.stream().filter(f -> f.fileName().toLowerCase().endsWith(".pdf")).findFirst().orElse(null);
        IncomingFile other = files.stream().filter(f -> f != pdf).findFirst().orElse(null);
        if (pdf == null || other == null || other.fileName().toLowerCase().endsWith(".pdf")) {
            return files;
        }
        return List.of(other, new IncomingFile(pdf.fileName(), FileRole.PREVIEW, pdf.source()));
    }

    @PostMapping("/artifacts/{id}/review")
    public String confirm(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user,
                          RedirectAttributes redirect) {
        service.confirmActual(id, user);
        redirect.addFlashAttribute("message", "artifacts.review.done");
        return "redirect:/artifacts/" + id;
    }

    @PostMapping("/artifacts/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        service.delete(id, user);
        return "redirect:/artifacts";
    }

    // ------------------------------------------------------------------ правка и создание по ссылке

    @GetMapping("/artifacts/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("form", service.formFor(id, user));
        model.addAttribute("artifactId", id);
        fillFormModel(model);
        return "artifacts/form";
    }

    @PostMapping("/artifacts/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute("form") ArtifactForm form,
                       @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            service.update(id, form, user);
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("artifactId", id);
            fillFormModel(model);
            return "artifacts/form";
        }
        return "redirect:/artifacts/" + id;
    }

    @GetMapping("/artifacts/new")
    public String newForm(@AuthenticationPrincipal HubUserDetails user, Model model) {
        ArtifactForm form = new ArtifactForm();
        form.setLanguage(Language.RU.name());
        form.setStatus(ArtifactStatus.ACTUAL.name());
        form.setOwnerId(user.getId());
        form.setContour(Contour.INTERNAL.name());
        model.addAttribute("form", form);
        fillFormModel(model);
        return "artifacts/form";
    }

    /** Материал, который живет вне HUB (YouTube, сайт, Confluence): хранится только ссылка. */
    @PostMapping("/artifacts")
    public String createLink(@ModelAttribute("form") ArtifactForm form, @AuthenticationPrincipal HubUserDetails user,
                             Model model) {
        try {
            var errors = new java.util.LinkedHashMap<String, String>();
            var type = form.getTypeId() == null ? null : types.byId(form.getTypeId()).orElse(null);
            var owner = form.getOwnerId() == null ? null : reference.owners().stream()
                    .filter(o -> o.getId().equals(form.getOwnerId())).findFirst().orElse(null);
            var skus = service.resolveSkus(form.getSkus(), errors);
            if (!errors.isEmpty()) {
                throw new FormValidationException(errors);
            }
            var line = form.getLineId() == null ? null : lines.findById(form.getLineId()).orElse(null);
            var tagList = form.getTagIds() == null ? List.<ru.eltex.producthub.tag.domain.Tag>of()
                    : tags.all().stream().filter(t -> form.getTagIds().contains(t.getId())).toList();
            var a = service.create(new ArtifactDraft(form.getTitle(), form.getDescription(), type,
                    parseLanguage(form.getLanguage()), parseStatus(form.getStatus()), owner,
                    parseContour(form.getContour()), form.getExternalUrl(), line, skus, tagList, null),
                    List.of(), null, user);
            return "redirect:/artifacts/" + a.getId();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            fillFormModel(model);
            return "artifacts/form";
        }
    }

    private void fillFormModel(Model model) {
        model.addAttribute("types", types.all());
        model.addAttribute("owners", reference.owners());
        model.addAttribute("lines", reference.lines());
        model.addAttribute("allTags", tags.active());
        model.addAttribute("statuses", ArtifactStatus.values());
        model.addAttribute("languages", Language.values());
        model.addAttribute("contours", Contour.values());
        model.addAttribute("activeMenu", "artifacts");
    }

    private static Language parseLanguage(String v) {
        try {
            return v == null ? Language.NONE : Language.valueOf(v);
        } catch (IllegalArgumentException e) {
            return Language.NONE;
        }
    }

    private static ArtifactStatus parseStatus(String v) {
        try {
            return v == null ? ArtifactStatus.ACTUAL : ArtifactStatus.valueOf(v);
        } catch (IllegalArgumentException e) {
            return ArtifactStatus.ACTUAL;
        }
    }

    private static Contour parseContour(String v) {
        try {
            return v == null ? null : Contour.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ массовые действия

    @PostMapping("/artifacts/bulk")
    public String bulk(@RequestParam(required = false) List<Long> ids, @RequestParam ArtifactService.BulkAction action,
                       @RequestParam(required = false) String value, @AuthenticationPrincipal HubUserDetails user,
                       RedirectAttributes redirect) {
        try {
            int n = service.bulk(ids, action, value, user);
            redirect.addFlashAttribute("message", "artifacts.bulk.done");
            redirect.addFlashAttribute("messageArg", n);
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("bulkError", e.getErrors().values().iterator().next());
        }
        return "redirect:/artifacts";
    }

    @ExceptionHandler(ArtifactNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/404";
    }

    @SuppressWarnings("unused")
    private static Map<String, String> none() {
        return Map.of();
    }
}
