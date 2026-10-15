package ru.eltex.producthub.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Откуда разрешено импортировать папки. Приложение читает файлы от имени пользователя компьютера, поэтому путь,
 * введенный в форме, должен лежать внутри заданных корней (producthub.import.folder-roots, через запятую).
 * Пустое значение отключает импорт папки полностью (так в профиле lan).
 */
@Component("folderImportPolicy")
public class FolderImportPolicy {

    private final List<Path> roots;

    public FolderImportPolicy(ImportProperties props) {
        String text = props.folderRoots() == null ? "" : props.folderRoots();
        this.roots = Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> real(Path.of(s)))
                .toList();
    }

    public boolean enabled() {
        return !roots.isEmpty();
    }

    /** Лежит ли папка внутри разрешенного корня (с учетом символических ссылок и "..") . */
    public boolean allows(Path folder) {
        Path real = real(folder);
        return roots.stream().anyMatch(real::startsWith);
    }

    public List<Path> roots() {
        return roots;
    }

    private static Path real(Path p) {
        try {
            return Files.exists(p) ? p.toRealPath() : p.toAbsolutePath().normalize();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }
}
