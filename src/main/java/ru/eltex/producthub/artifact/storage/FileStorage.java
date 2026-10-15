package ru.eltex.producthub.artifact.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Хранилище файлов (SPEC.md раздел 3). Сейчас файлы лежат на диске под UUID-именами, позже реализацию можно
 * заменить на S3 или MinIO, не меняя остальной код.
 */
public interface FileStorage {

    /** Сохраняет поток и возвращает ключ, размер и sha256. Оригинальное имя в хранилище не используется. */
    StoredFile store(InputStream in) throws IOException;

    /** Путь к файлу для отдачи (для локального хранилища). */
    Path resolve(String key);

    void delete(String key) throws IOException;

    record StoredFile(String key, long size, String sha256) {
    }
}
