package ru.eltex.producthub.sku.service;

import java.util.Map;

import ru.eltex.producthub.common.FormValidationException;

/** Ошибки заполнения формы SKU: поле -> ключ сообщения из messages_ru.properties. */
public class SkuValidationException extends FormValidationException {

    public SkuValidationException(Map<String, String> errors) {
        super(errors);
    }
}
