package ru.eltex.producthub.firmware.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FirmwareNotFoundException extends RuntimeException {

    public FirmwareNotFoundException(Long id) {
        super("Прошивка не найдена: " + id);
    }
}
