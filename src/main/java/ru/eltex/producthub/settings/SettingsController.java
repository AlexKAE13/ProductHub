package ru.eltex.producthub.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.security.HubUserDetails;

@Controller
public class SettingsController {

    private final UserSettingsService service;
    private final WallpaperService wallpapers;

    public SettingsController(UserSettingsService service, WallpaperService wallpapers) {
        this.service = service;
        this.wallpapers = wallpapers;
    }

    @GetMapping("/settings")
    public String page(@AuthenticationPrincipal HubUserDetails user, Model model) {
        model.addAttribute("form", SettingsForm.from(service.get(user.getId())));
        model.addAttribute("layouts", MenuLayout.values());
        model.addAttribute("densities", Density.values());
        model.addAttribute("startPages", StartPage.values());
        model.addAttribute("themes", UiTheme.values());
        model.addAttribute("modes", ColorMode.values());
        model.addAttribute("palettes", Palette.values());
        model.addAttribute("backgrounds", Background.values());
        model.addAttribute("look", service.get(user.getId()).look());
        model.addAttribute("builtInWalls", java.util.List.of("dunes", "peaks", "aurora"));
        model.addAttribute("activeMenu", "settings");
        return "settings/index";
    }

    /** Настройки меняет только сам пользователь и только свои. */
    @PostMapping("/settings")
    public String save(@ModelAttribute("form") SettingsForm form, @AuthenticationPrincipal HubUserDetails user,
                       RedirectAttributes redirect) {
        service.save(user.getId(), form.toView(service.get(user.getId()).look().wallpaperKey()));
        redirect.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }

    /** Кнопка "свернуть сайдбар": запоминается сразу, без перезагрузки страницы. */
    @PostMapping("/settings/sidebar")
    public ResponseEntity<Void> sidebar(@RequestParam boolean collapsed, @AuthenticationPrincipal HubUserDetails user) {
        service.setSidebarCollapsed(user.getId(), collapsed);
        return ResponseEntity.noContent().build();
    }

    /** Кнопка "светлая/темная" в меню: запоминается сразу, без перезагрузки страницы. */
    @PostMapping("/settings/mode")
    public ResponseEntity<Void> mode(@RequestParam ColorMode mode, @AuthenticationPrincipal HubUserDetails user) {
        service.setColorMode(user.getId(), mode);
        return ResponseEntity.noContent().build();
    }

    /** Загрузка своих обоев (JPEG или PNG до 5 МБ). */
    @PostMapping("/settings/wallpaper")
    public String upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal HubUserDetails user,
                         RedirectAttributes redirect) {
        try {
            wallpapers.upload(user.getId(), file);
            redirect.addFlashAttribute("saved", true);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("wallError", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/wallpaper/delete")
    public String deleteWallpaper(@AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        wallpapers.remove(user.getId());
        redirect.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }

    /** Отдает обои текущего пользователя. Чужие обои получить нельзя: файл выбирается по пользователю из сессии. */
    @GetMapping("/settings/wallpaper")
    public ResponseEntity<Resource> wallpaper(@AuthenticationPrincipal HubUserDetails user) {
        Path file = wallpapers.file(user.getId());
        if (file == null || !Files.isReadable(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
