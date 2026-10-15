package ru.eltex.producthub.artifact.event;

/** Событие: загружен артефакт или его новая версия (SPEC.md 3.2). Слушатели появятся на этапе 4. */
public record ArtifactUpdated(Long artifactId, int versionNo, boolean created, String userLogin) {
}
