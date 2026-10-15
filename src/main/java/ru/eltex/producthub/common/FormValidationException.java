package ru.eltex.producthub.common;

import java.util.Map;

/** Ошибки заполнения формы: поле -> ключ сообщения из messages_ru.properties. */
public class FormValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public FormValidationException(Map<String, String> errors) {
        super("Ошибки заполнения: " + errors);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
