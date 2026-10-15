package ru.eltex.producthub.artifact.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.domain.FileRole;

/** Файл, который нужно сохранить в версию артефакта: имя, роль и источник данных. */
public record IncomingFile(String fileName, FileRole role, IoSupplier source) {

    @FunctionalInterface
    public interface IoSupplier {
        InputStream open() throws IOException;
    }

    public static IncomingFile of(Path path, FileRole role) {
        return new IncomingFile(path.getFileName().toString(), role, () -> Files.newInputStream(path));
    }

    public static IncomingFile of(MultipartFile file, FileRole role) {
        String name = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
        return new IncomingFile(name, role, file::getInputStream);
    }
}
