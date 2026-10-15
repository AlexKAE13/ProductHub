package ru.eltex.producthub.artifact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(Long id) {
        super("Артефакт не найден: " + id);
    }
}
