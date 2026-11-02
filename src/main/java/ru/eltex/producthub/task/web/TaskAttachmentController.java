package ru.eltex.producthub.task.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.artifact.storage.FileTypes;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.task.service.TaskAttachmentService;

@Controller
public class TaskAttachmentController {
    private final TaskAttachmentService attachments;

    public TaskAttachmentController(TaskAttachmentService attachments) { this.attachments = attachments; }

    @PostMapping("/tasks/{id}/attachments")
    public String upload(@PathVariable long id, @RequestParam("file") List<MultipartFile> files,
                         @AuthenticationPrincipal HubUserDetails user, RedirectAttributes redirect) throws IOException {
        if (files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) {
            redirect.addFlashAttribute("attachmentError", true);
        } else {
            for (MultipartFile file : files) {
                attachments.upload(id, file, user);
            }
        }
        return "redirect:/tasks/" + id;
    }

    @GetMapping("/tasks/{id}/attachments/{fileId}")
    public ResponseEntity<FileSystemResource> download(@PathVariable long id, @PathVariable long fileId,
                                                       @RequestParam(defaultValue = "false") boolean inline,
                                                       @AuthenticationPrincipal HubUserDetails user) throws IOException {
        var file = attachments.get(id, fileId, user);
        boolean show = false;
        if (inline && file.isPreviewable()) {
            try (var in = Files.newInputStream(attachments.path(file))) {
                show = FileTypes.looksValid(FileTypes.extension(file.name()), in.readNBytes(1024));
            }
        }
        MediaType type = show ? (FileTypes.isPlainText(file.name())
                ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                : MediaType.parseMediaType(FileTypes.mime(file.name()))) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, (show ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(new FileSystemResource(attachments.path(file)));
    }
}
