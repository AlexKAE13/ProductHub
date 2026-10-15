package ru.eltex.producthub.artifact.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Расширение, MIME и проверка, что содержимое файла похоже на заявленный тип (SPEC.md 8.2). */
public final class FileTypes {

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("zip", "application/zip"),
            Map.entry("md", "text/markdown"),
            Map.entry("txt", "text/plain"));

    private FileTypes() {
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String mime(String fileName) {
        return MIME.getOrDefault(extension(fileName), "application/octet-stream");
    }

    /**
     * Показывать в браузере без скачивания можно только безопасные форматы: PDF, растровые картинки, видео mp4 и простой
     * текст. svg (может содержать скрипты) и документы Office браузер не откроет, они скачиваются.
     */
    public static boolean isInlineSafe(String fileName) {
        return switch (extension(fileName)) {
            case "pdf", "png", "jpg", "jpeg", "mp4", "txt", "md" -> true;
            default -> false;
        };
    }

    /** Текстовые файлы при просмотре отдаются как простой текст в UTF-8, чтобы браузер показал их, а не скачал. */
    public static boolean isPlainText(String fileName) {
        return switch (extension(fileName)) {
            case "txt", "md" -> true;
            default -> false;
        };
    }

    public static boolean isImage(String fileName) {
        return switch (extension(fileName)) {
            case "png", "jpg", "jpeg", "svg" -> true;
            default -> false;
        };
    }

    public static boolean looksValid(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return looksValid(extension(file.getFileName().toString()), in.readNBytes(1024));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean looksValid(String ext, byte[] head) {
        if (head.length == 0) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> startsWith(head, '%', 'P', 'D', 'F');
            case "png" -> head.length > 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "jpg", "jpeg" -> head.length > 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "pptx", "docx", "xlsx", "zip" -> startsWith(head, 'P', 'K');
            case "mp4" -> head.length > 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            case "svg", "md", "txt" -> noBinary(head);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] head, char... chars) {
        if (head.length < chars.length) {
            return false;
        }
        for (int i = 0; i < chars.length; i++) {
            if (head[i] != chars[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean noBinary(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }
}
