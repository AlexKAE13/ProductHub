package ru.eltex.producthub.qa.service;

import java.util.List;
import java.util.Set;

import ru.eltex.producthub.qa.domain.Question;
import ru.eltex.producthub.qa.domain.TargetType;

/** Блок "FAQ и вопросы и правки" на странице объекта (SPEC.md 5.3, блок 12). */
public record QaView(
        TargetType targetType,
        Long targetId,
        String backUrl,
        List<Question> faq,
        List<Question> open,
        Set<Long> answerable,
        boolean canAsk) {
}
