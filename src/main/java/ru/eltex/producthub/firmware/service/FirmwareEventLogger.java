package ru.eltex.producthub.firmware.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ru.eltex.producthub.firmware.event.FirmwarePublished;

/** Этап 3: событие только пишется в журнал. Этап 4 добавит слушатель, который создает задачи владельцам SKU. */
@Component
public class FirmwareEventLogger {

    private static final Logger log = LoggerFactory.getLogger(FirmwareEventLogger.class);

    @EventListener
    public void on(FirmwarePublished event) {
        log.info("Опубликована прошивка {} (№{}), SKU: {}, автор: {}", event.version(), event.firmwareId(),
                event.skuIds(), event.userLogin());
    }
}
