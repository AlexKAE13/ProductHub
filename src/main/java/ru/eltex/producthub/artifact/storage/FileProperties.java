package ru.eltex.producthub.artifact.storage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Настройки файлов: папка хранения, лимит размера, разрешенные расширения (SPEC.md 5.5, 8.2). */
@ConfigurationProperties(prefix = "producthub.files")
public record FileProperties(
        @DefaultValue("./data/files") String dir,
        @DefaultValue("200") int maxSizeMb,
        @DefaultValue({"pdf", "pptx", "docx", "xlsx", "png", "jpg", "svg", "mp4", "zip", "md", "txt"})
        List<String> allowedExtensions) {

    public long maxSizeBytes() {
        return maxSizeMb * 1024L * 1024L;
    }
}
