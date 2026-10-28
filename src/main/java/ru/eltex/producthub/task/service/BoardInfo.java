package ru.eltex.producthub.task.service;

import java.util.List;
import java.util.Map;

import ru.eltex.producthub.task.domain.TaskStatus;

/** Свои данные доски для показа (без ленивых связей): настройки, участники, колонки. */
public record BoardInfo(Long id, String name, String description, Long ownerId, String ownerName,
                        List<Member> members, Map<TaskStatus, String> titles, Map<TaskStatus, String> colors,
                        boolean owner) {

    public record Member(Long id, String fullName, boolean owner) {
    }
}
