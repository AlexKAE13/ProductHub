package ru.eltex.producthub.casestudy.web;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.casestudy.service.CaseFilter;
import ru.eltex.producthub.casestudy.service.CaseForm;
import ru.eltex.producthub.casestudy.service.CaseService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.tag.service.TagService;

@Controller
public class CaseController {

    private final CaseService service;
    private final ReferenceDataService reference;
    private final TagService tagService;

    public CaseController(CaseService service, ReferenceDataService reference, TagService tagService) {
        this.service = service;
        this.reference = reference;
        this.tagService = tagService;
    }

    @GetMapping("/cases")
    public String list(@RequestParam(required = false) String country, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Long tag, @RequestParam(required = false) String sku,
                       @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                       @RequestHeader(value = "HX-Request", required = false) String htmx,
                       @AuthenticationPrincipal HubUserDetails user, Model model) {
        CaseFilter filter = new CaseFilter(country, year, tag, sku, q, page);
        var result = service.search(filter, user);
        model.addAttribute("page", result);
        model.addAttribute("data", service.listData(result.items()));
        model.addAttribute("filter", filter);
        model.addAttribute("canEdit", service.canEdit(user));
        model.addAttribute("activeMenu", "cases");
        if (htmx != null) {
            return "cases/index :: results";
        }
        model.addAttribute("countries", service.countries());
        model.addAttribute("years", service.years());
        model.addAttribute("allTags", tagService.active());
        return "cases/index";
    }

    @GetMapping("/cases/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("d", service.detail(id, user));
        model.addAttribute("activeMenu", "cases");
        return "cases/detail";
    }

    @GetMapping("/cases/new")
    public String newForm(@RequestParam(required = false) String sku, @AuthenticationPrincipal HubUserDetails user,
                          Model model) {
        requireEdit(user);
        CaseForm form = new CaseForm();
        form.setOwnerId(user.getId());
        form.setSkus(sku);
        form.setYear(String.valueOf(java.time.LocalDate.now().getYear()));
        fillForm(model);
        model.addAttribute("form", form);
        return "cases/form";
    }

    @PostMapping("/cases")
    public String create(@ModelAttribute("form") CaseForm form, @AuthenticationPrincipal HubUserDetails user,
                         Model model) {
        try {
            return "redirect:/cases/" + service.create(form, user).getId();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            fillForm(model);
            return "cases/form";
        }
    }

    @GetMapping("/cases/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("form", service.formFor(id, user));
        model.addAttribute("caseId", id);
        fillForm(model);
        return "cases/form";
    }

    @PostMapping("/cases/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute("form") CaseForm form,
                       @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            service.update(id, form, user);
            return "redirect:/cases/" + id;
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("caseId", id);
            fillForm(model);
            return "cases/form";
        }
    }

    @PostMapping("/cases/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        service.delete(id, user);
        return "redirect:/cases";
    }

    @PostMapping("/cases/{id}/attachments")
    public String addAttachments(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files,
                                 @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        try {
            service.addAttachments(id, files, user);
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("attachmentError", e.getErrors().values().iterator().next());
        }
        return "redirect:/cases/" + id;
    }

    @PostMapping("/cases/{id}/attachments/{artifactId}/delete")
    public String removeAttachment(@PathVariable Long id, @PathVariable Long artifactId,
                                   @AuthenticationPrincipal HubUserDetails user) {
        service.removeAttachment(id, artifactId, user);
        return "redirect:/cases/" + id;
    }

    private void fillForm(Model model) {
        model.addAttribute("owners", reference.owners());
        model.addAttribute("allTags", tagService.active());
        model.addAttribute("countries", service.countries());
        model.addAttribute("activeMenu", "cases");
    }

    private void requireEdit(HubUserDetails user) {
        if (!service.canEdit(user)) {
            throw new AccessDeniedException("Кейсы создают только PRODUCT и ADMIN");
        }
    }
}
