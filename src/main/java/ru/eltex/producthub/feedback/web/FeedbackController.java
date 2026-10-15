package ru.eltex.producthub.feedback.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.feedback.domain.CommentKind;
import ru.eltex.producthub.feedback.domain.CommentStatus;
import ru.eltex.producthub.feedback.service.CommentInput;
import ru.eltex.producthub.feedback.service.CommentView;
import ru.eltex.producthub.feedback.service.FeedbackService;
import ru.eltex.producthub.security.HubUserDetails;

/** Комментарии прямо на страницах: JSON для встроенной панели, страница разбора и выгрузка файла правок. */
@Controller
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    // ------------------------------------------------------------ панель на странице (JSON)

    @GetMapping("/feedback/comments")
    @ResponseBody
    public List<CommentView> forPage(@RequestParam String path, @AuthenticationPrincipal HubUserDetails user) {
        return service.forPage(path, user);
    }

    @PostMapping("/feedback/comments")
    public ResponseEntity<Object> create(@RequestBody CommentInput input, @AuthenticationPrincipal HubUserDetails user) {
        try {
            return ResponseEntity.ok(service.create(input, user));
        } catch (FormValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", e.getErrors()));
        }
    }

    @PostMapping("/feedback/comments/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal HubUserDetails user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ разбор

    @GetMapping("/feedback")
    public String manage(@RequestParam(required = false) String status, @RequestParam(required = false) String page,
                         @RequestParam(required = false) String author, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        ui.addAttribute("comments", service.all(status, page, author, user));
        ui.addAttribute("pages", service.pages());
        ui.addAttribute("statuses", CommentStatus.values());
        ui.addAttribute("kinds", CommentKind.values());
        ui.addAttribute("fStatus", status == null ? "" : status);
        ui.addAttribute("fPage", page == null ? "" : page);
        ui.addAttribute("fAuthor", author == null ? "" : author);
        ui.addAttribute("activeMenu", "feedback");
        return "feedback/index";
    }

    @PostMapping("/feedback/comments/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam CommentStatus status, @RequestParam(required = false) String note,
                            @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) {
        service.setStatus(id, status, note, user);
        return "redirect:/feedback";
    }

    /** Отметка "решено" одним нажатием: из панели на странице (ответ 204) или со страницы разбора (возврат на нее). */
    @PostMapping("/feedback/comments/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean resolved,
                                        @RequestParam(required = false) String back, @AuthenticationPrincipal HubUserDetails user) {
        service.setResolved(id, resolved, user);
        if (back != null && back.startsWith("/feedback")) {
            return ResponseEntity.status(303).header(HttpHeaders.LOCATION, back).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/feedback/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(required = false) String page,
                                         @RequestParam(required = false) String author,
                                         @AuthenticationPrincipal HubUserDetails user) {
        String md = service.exportMarkdown(status, page, author, user);
        String name = "product-hub-comments-" + LocalDate.now() + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(md.getBytes(StandardCharsets.UTF_8));
    }
}
