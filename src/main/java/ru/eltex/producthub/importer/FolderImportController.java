package ru.eltex.producthub.importer;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactPermissions;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.service.ReferenceDataService;

/** Загрузка файлов перетаскиванием и импорт папки: предпросмотр, правка, подтверждение (SPEC.md 5.5, 5.11). */
@Controller
public class FolderImportController {

    private final FolderImportService service;
    private final ImportPlanStore store;
    private final ArtifactTypeService types;
    private final ReferenceDataService reference;
    private final ArtifactPermissions permissions;
    private final FolderImportPolicy policy;

    public FolderImportController(FolderImportService service, ImportPlanStore store, ArtifactTypeService types,
                                  ReferenceDataService reference, ArtifactPermissions permissions,
                                  FolderImportPolicy policy) {
        this.policy = policy;
        this.service = service;
        this.store = store;
        this.types = types;
        this.reference = reference;
        this.permissions = permissions;
    }

    @GetMapping("/artifacts/upload")
    public String uploadPage(@AuthenticationPrincipal HubUserDetails user, Model model) {
        requireEdit(user);
        model.addAttribute("activeMenu", "artifacts");
        return "importer/upload";
    }

    @PostMapping("/artifacts/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal HubUserDetails user,
                         Model model) {
        try {
            return "redirect:/artifacts/import/" + service.stageUploads(files, user).getToken();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("activeMenu", "artifacts");
            return "importer/upload";
        }
    }

    @GetMapping("/artifacts/import")
    public String folderPage(@AuthenticationPrincipal HubUserDetails user, Model model) {
        requireManage(user);
        if (!policy.enabled()) {
            throw new AccessDeniedException("Импорт папки на этом сервере отключен");
        }
        model.addAttribute("roots", policy.roots());
        model.addAttribute("activeMenu", "artifacts");
        return "importer/folder";
    }

    @PostMapping("/artifacts/import/scan")
    public String scan(@RequestParam String path, @AuthenticationPrincipal HubUserDetails user, Model model) {
        try {
            return "redirect:/artifacts/import/" + service.scanFolder(path, user).getToken();
        } catch (FormValidationException e) {
            model.addAttribute("errors", e.getErrors());
            model.addAttribute("path", path);
            model.addAttribute("activeMenu", "artifacts");
            return "importer/folder";
        }
    }

    @GetMapping("/artifacts/import/{token}")
    public String preview(@PathVariable String token, @AuthenticationPrincipal HubUserDetails user, Model model,
                          RedirectAttributes redirect) {
        ImportPlan plan = store.get(token);
        if (plan == null) {
            redirect.addFlashAttribute("planExpired", true);
            return "redirect:/artifacts/upload";
        }
        if (!plan.getCreatedBy().equals(user.getUsername())) {
            throw new AccessDeniedException("План импорта принадлежит другому пользователю");
        }
        model.addAttribute("plan", plan);
        model.addAttribute("types", types.all());
        model.addAttribute("owners", reference.owners());
        model.addAttribute("languages", Language.values());
        model.addAttribute("statuses", ArtifactStatus.values());
        model.addAttribute("contours", Contour.values());
        model.addAttribute("activeMenu", "artifacts");
        return "importer/preview";
    }

    @PostMapping("/artifacts/import/{token}/confirm")
    public String confirm(@PathVariable String token, @ModelAttribute PlanForm form,
                          @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        try {
            ImportRun run = service.confirm(token, form.getRows(), form.isDryRun(), user);
            redirect.addFlashAttribute("token", form.isDryRun() ? token : null);
            return "redirect:/artifacts/import/runs/" + run.getId();
        } catch (FormValidationException e) {
            redirect.addFlashAttribute("planExpired", true);
            return "redirect:/artifacts/upload";
        }
    }

    @GetMapping("/artifacts/import/runs/{id}")
    public String result(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user, Model model) {
        requireEdit(user);
        model.addAttribute("run", service.run(id));
        model.addAttribute("activeMenu", "artifacts");
        return "importer/run";
    }

    private void requireEdit(HubUserDetails user) {
        if (!permissions.canEdit(user)) {
            throw new AccessDeniedException("Нет прав на загрузку артефактов");
        }
    }

    private void requireManage(HubUserDetails user) {
        if (!permissions.canManage(user)) {
            throw new AccessDeniedException("Импорт папки доступен только PRODUCT и ADMIN");
        }
    }
}
