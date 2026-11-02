package ru.eltex.producthub.task.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.storage.FileStorage;
import ru.eltex.producthub.security.HubUserDetails;

@Service
public class TaskAttachmentService {
    private final TaskService tasks;
    private final FileStorage storage;
    private final JdbcTemplate jdbc;

    public TaskAttachmentService(TaskService tasks, FileStorage storage, JdbcTemplate jdbc) {
        this.tasks = tasks;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    public record Attachment(long id, String name, long size, String key) {
        public boolean isPreviewable() {
            return ru.eltex.producthub.artifact.storage.FileTypes.isInlineSafe(name);
        }
    }

    public List<Attachment> list(long taskId, HubUserDetails user) {
        tasks.detail(taskId, user);
        return jdbc.query("select id, file_name, file_size, storage_key from task_attachment where task_id = ? order by id",
                (rs, n) -> new Attachment(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)), taskId);
    }

    public void upload(long taskId, MultipartFile file, HubUserDetails user) throws IOException {
        var task = tasks.detail(taskId, user);
        if (!tasks.canMove(user, task)) throw new AccessDeniedException("Нет прав на изменение задачи");
        if (file.isEmpty()) throw new IllegalArgumentException("Файл не выбран или пуст");
        String name = file.getOriginalFilename();
        name = name == null ? "file" : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.isBlank()) name = "file";
        if (name.length() > 255) name = name.substring(name.length() - 255);
        FileStorage.StoredFile stored;
        try (var in = file.getInputStream()) { stored = storage.store(in); }
        try {
            jdbc.update("insert into task_attachment(task_id, file_name, storage_key, file_size, uploaded_at) values (?, ?, ?, ?, ?)",
                    taskId, name, stored.key(), stored.size(), LocalDateTime.now());
        } catch (RuntimeException e) {
            storage.delete(stored.key());
            throw e;
        }
    }

    public Attachment get(long taskId, long attachmentId, HubUserDetails user) {
        return list(taskId, user).stream().filter(a -> a.id() == attachmentId).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    public Path path(Attachment attachment) { return storage.resolve(attachment.key()); }
}
