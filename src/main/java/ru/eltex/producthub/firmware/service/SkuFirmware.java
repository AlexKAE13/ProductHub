package ru.eltex.producthub.firmware.service;

import java.util.List;

import ru.eltex.producthub.firmware.domain.Firmware;

/** Прошивки SKU для блока 4 карточки: текущая (последняя RELEASE) и остальные. */
public record SkuFirmware(Firmware current, List<Firmware> others, boolean canPublish) {

    public boolean isEmpty() {
        return current == null && others.isEmpty();
    }
}
