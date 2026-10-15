package ru.eltex.producthub.artifact.event;

/** Событие: артефакт не ревьюили дольше его периода (ежедневная проверка). Статус артефакта не меняется. */
public record ArtifactStale(Long artifactId) {
}
