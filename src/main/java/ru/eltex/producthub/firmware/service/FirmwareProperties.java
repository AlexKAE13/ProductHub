package ru.eltex.producthub.firmware.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Настройки прошивок. Расширения файлов не ограничиваются: форматы у разных изделий разные. */
@ConfigurationProperties(prefix = "producthub.firmware")
public record FirmwareProperties(@DefaultValue("1024") int maxSizeMb) {

    public long maxSizeBytes() {
        return maxSizeMb * 1024L * 1024L;
    }
}
