package ru.eltex.producthub.artifact.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Файлы на диске: {корень}/ab/ab12... (первые два символа UUID - подпапка). */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FileProperties props) throws IOException {
        this.root = Path.of(props.dir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile store(InputStream in) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream din = new DigestInputStream(in, digest); OutputStream out = Files.newOutputStream(tmp)) {
                size = din.transferTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, size, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public Path resolve(String key) {
        if (!key.matches("[0-9a-f\\-]{36}")) {
            throw new IllegalArgumentException("Недопустимый ключ файла");
        }
        return root.resolve(key.substring(0, 2)).resolve(key);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }
}
