package ru.eltex.producthub.qa.service;

import ru.eltex.producthub.qa.domain.Question;

/** Подсказка "Возможно, ответ уже есть". */
public record SimilarQuestion(Question question, double score, String targetUrl) {
}
