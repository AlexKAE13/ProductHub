package ru.eltex.producthub.common;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** JPA-слушатель: сообщает модулю поиска, что объект сохранен. Подключается через @EntityListeners. */
@Component
public class IndexedChangeListener {

    private final ApplicationEventPublisher events;

    public IndexedChangeListener(ApplicationEventPublisher events) {
        this.events = events;
    }

    @PostPersist
    @PostUpdate
    public void changed(Object entity) {
        if (entity instanceof Indexable indexable && indexable.indexId() != null) {
            events.publishEvent(new IndexedEntityChanged(indexable.indexType(), indexable.indexId()));
        }
    }
}
