package ru.eltex.producthub.artifact.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.storage.FileStorage;
import ru.eltex.producthub.artifact.storage.FileTypes;
import ru.eltex.producthub.security.HubUserDetails;

/** Отдача файлов потоком с поддержкой Range и ETag (SPEC.md 8.1). */
@Controller
public class FileController {

    private final ArtifactService service;
    private final FileStorage storage;

    public FileController(ArtifactService service, FileStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping("/artifacts/files/{id}")
    public ResponseEntity<Resource> file(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean inline,
                                         @AuthenticationPrincipal HubUserDetails user) {
        ArtifactFile f = service.fileForDownload(id, user);
        Resource resource = new FileSystemResource(storage.resolve(f.getStoredPath()));
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // В браузере показываем только безопасные форматы, остальное и svg (скрипты) скачивается
        boolean show = inline && FileTypes.isInlineSafe(f.getFileName());
        ContentDisposition disposition = (show ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(f.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .eTag("\"" + f.getSha256() + "\"")
                .cacheControl(org.springframework.http.CacheControl.noCache().cachePrivate())
                .contentType(show && FileTypes.isPlainText(f.getFileName())
                        ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                        : MediaType.parseMediaType(f.getMimeType() == null ? "application/octet-stream" : f.getMimeType()))
                .contentLength(f.getSizeBytes())
                .body(resource);
    }
}
