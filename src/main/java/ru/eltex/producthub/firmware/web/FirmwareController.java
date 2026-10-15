package ru.eltex.producthub.firmware.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import ru.eltex.producthub.artifact.storage.FileStorage;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.firmware.domain.Firmware;
import ru.eltex.producthub.firmware.domain.FirmwareStatus;
import ru.eltex.producthub.firmware.service.FirmwareFilter;
import ru.eltex.producthub.firmware.service.FirmwareForm;
import ru.eltex.producthub.firmware.service.FirmwareService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.ReferenceDataService;

@Controller
public class FirmwareController {

    private final FirmwareService service;
    private final FileStorage storage;
    private final ReferenceDataService reference;
    private final ru.eltex.producthub.qa.service.QuestionService questionService;

    public FirmwareController(FirmwareService service, FileStorage storage, ReferenceDataService reference,
                              ru.eltex.producthub.qa.service.QuestionService questionService) {
        this.service = service;
        this.storage = storage;
        this.reference = reference;
        this.questionService = questionService;
    }

    @GetMapping("/firmware")
    public String list(@RequestParam(required = false) String sku, @RequestParam(required = false) Long line,
                       @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestHeader(value = "HX-Request", required = false) String htmx,
                       @AuthenticationPrincipal HubUserDetails user, Model model) {
        FirmwareFilter filter = new FirmwareFilter(sku, line, status, q, page);
        var result = service.search(filter, user);
        model.addAttribute("page", result);
        model.addAttribute("skusByFirmware", service.skusFor(result.items()));
        model.addAttribute("filter", filter);
        model.addAttribute("canPublish", service.canPublish(user));
        model.addAttribute("activeMenu", "firmware");
        if (htmx != null) {
            return "firmware/index :: results";
        }
        model.addAttribute("lines", reference.lines());
        model.addAttribute("statuses", FirmwareStatus.values());
        return "firmware/index";
    }

    @GetMapping("/firmware/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("d", service.detail(id, user));
        model.addAttribute("qa", questionService.view(ru.eltex.producthub.qa.domain.TargetType.FIRMWARE, id, user));
        model.addAttribute("statuses", FirmwareStatus.values());
        model.addAttribute("activeMenu", "firmware");
        return "firmware/detail";
    }

    // ------------------------------------------------------------------ публикация и правка

    @GetMapping("/firmware/new")
    public String newForm(@RequestParam(required = false) String sku, @AuthenticationPrincipal HubUserDetails user,
                          Model model) {
        requirePublish(user);
        FirmwareForm form = new FirmwareForm();
        form.setReleaseDate(java.time.LocalDate.now().toString());
        form.setSkus(sku);
        fillForm(model);
        model.addAttribute("form", form);
        return "firmware/form";
    }

    @PostMapping("/firmware")
    public String create(@ModelAttribute("form") FirmwareForm form,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         @AuthenticationPrincipal HubUserDetails user, Model model, RedirectAttributes redirect) {
        try {
            var saved = service.create(form, file, user);
            redirect.addFlashAttribute("warnings", saved.warnings());
            return "redirect:/firmware/" + saved.firmware().getId();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            fillForm(model);
            return "firmware/form";
        }
    }

    @GetMapping("/firmware/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        requirePublish(user);
        model.addAttribute("form", service.formFor(id, user));
        model.addAttribute("firmwareId", id);
        fillForm(model);
        return "firmware/form";
    }

    @PostMapping("/firmware/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute("form") FirmwareForm form,
                       @RequestParam(value = "file", required = false) MultipartFile file,
                       @AuthenticationPrincipal HubUserDetails user, Model model, RedirectAttributes redirect) {
        try {
            var saved = service.update(id, form, file, user);
            redirect.addFlashAttribute("warnings", saved.warnings());
            return "redirect:/firmware/" + id;
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("firmwareId", id);
            fillForm(model);
            return "firmware/form";
        }
    }

    @PostMapping("/firmware/{id}/status")
    public String status(@PathVariable Long id, @RequestParam FirmwareStatus status,
                         @AuthenticationPrincipal HubUserDetails user) {
        service.changeStatus(id, status, user);
        return "redirect:/firmware/" + id;
    }

    @PostMapping("/firmware/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        service.delete(id, user);
        return "redirect:/firmware";
    }

    // ------------------------------------------------------------------ скачивание

    /** Файл прошивки отдается как вложение, потоком, с Range и ETag. Формат не проверяется: у изделий разные. */
    @GetMapping("/firmware/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        Firmware f = service.fileForDownload(id, user);
        Resource resource = new FileSystemResource(storage.resolve(f.getStoredPath()));
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .eTag("\"" + f.getSha256() + "\"")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.getSizeBytes())
                .body(resource);
    }

    private void fillForm(Model model) {
        model.addAttribute("statuses", FirmwareStatus.values());
        model.addAttribute("activeMenu", "firmware");
    }

    private void requirePublish(HubUserDetails user) {
        if (!service.canPublish(user)) {
            throw new AccessDeniedException("Нет прав на публикацию прошивок");
        }
    }
}
