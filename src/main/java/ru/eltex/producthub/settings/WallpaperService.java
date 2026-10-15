package ru.eltex.producthub.settings;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.storage.FileStorage;

/**
 * Личные обои для современного оформления. Картинка уменьшается до 1920 px по длинной стороне и сохраняется как JPEG,
 * поэтому тяжелые фото не замедляют страницы. Обои видит только их владелец.
 */
@Service
public class WallpaperService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_SIDE = 1920;

    private final FileStorage storage;
    private final UserSettingsService settings;

    public WallpaperService(FileStorage storage, UserSettingsService settings) {
        this.storage = storage;
        this.settings = settings;
    }

    public void upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("settings.wallpaper.empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("settings.wallpaper.tooBig");
        }
        byte[] jpeg;
        try (InputStream in = file.getInputStream()) {
            jpeg = resize(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("settings.wallpaper.bad");
        }
        try {
            FileStorage.StoredFile stored = storage.store(new ByteArrayInputStream(jpeg));
            UserSettingsView current = settings.get(userId);
            deleteQuietly(current.look().wallpaperKey());
            Look look = current.look();
            settings.save(userId, current.withLook(new Look(look.theme(), look.mode(), look.palette(),
                    Background.PHOTO, "custom", stored.key(), look.blur(), look.dim())));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить обои", e);
        }
    }

    public void remove(Long userId) {
        UserSettingsView current = settings.get(userId);
        deleteQuietly(current.look().wallpaperKey());
        Look look = current.look();
        settings.save(userId, current.withLook(new Look(look.theme(), look.mode(), look.palette(), look.background(),
                "dunes", null, look.blur(), look.dim())));
    }

    /** Файл обоев пользователя или null. */
    public Path file(Long userId) {
        String key = settings.get(userId).look().wallpaperKey();
        return key == null ? null : storage.resolve(key);
    }

    static byte[] resize(InputStream in) throws IOException {
        BufferedImage src = ImageIO.read(in);
        if (src == null) {
            throw new IOException("not an image");
        }
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (IOException ignored) {
            // старый файл не найден или занят - не мешает замене
        }
    }
}
