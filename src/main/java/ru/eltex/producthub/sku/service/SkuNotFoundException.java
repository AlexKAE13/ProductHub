package ru.eltex.producthub.sku.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SkuNotFoundException extends RuntimeException {

    public SkuNotFoundException(String model) {
        super("SKU не найден: " + model);
    }
}
