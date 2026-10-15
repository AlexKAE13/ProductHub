package ru.eltex.producthub.importer;

import java.nio.file.Path;

import ru.eltex.producthub.artifact.domain.FileRole;

/** Файл в плане импорта. */
public record PlanFile(Path path, String relativePath, long size, String sha256, FileRole role) {

    public String fileName() {
        return path.getFileName().toString();
    }
}
