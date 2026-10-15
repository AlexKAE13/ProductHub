package ru.eltex.producthub.casestudy.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(Long id) {
        super("Кейс не найден: " + id);
    }
}
