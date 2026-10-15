package ru.eltex.producthub.firmware.event;

import java.util.List;

/**
 * Событие: опубликована прошивка со статусом RELEASE (SPEC.md 3.2, 5.6). На этапе 3 его только пишут в журнал,
 * задачи владельцам SKU создает этап 4.
 */
public record FirmwarePublished(Long firmwareId, String version, List<Long> skuIds, String userLogin) {
}
