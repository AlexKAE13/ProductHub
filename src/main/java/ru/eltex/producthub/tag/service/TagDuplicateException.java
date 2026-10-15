package ru.eltex.producthub.tag.service;

import java.util.Map;

import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.tag.domain.Tag;

/** Такой тег уже есть (с учетом регистра, пробелов, дефисов, английского названия и синонимов). */
public class TagDuplicateException extends FormValidationException {

    private final transient Tag existing;

    public TagDuplicateException(Tag existing) {
        super(Map.of("value", "validation.tag.exists"));
        this.existing = existing;
    }

    public Tag getExisting() {
        return existing;
    }
}
