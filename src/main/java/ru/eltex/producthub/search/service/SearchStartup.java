package ru.eltex.producthub.search.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Если индекс пуст (первый запуск или удалена папка индекса), строит его из базы. */
@Component
public class SearchStartup {

    private final SearchIndex index;
    private final SearchIndexer indexer;

    public SearchStartup(SearchIndex index, SearchIndexer indexer) {
        this.index = index;
        this.indexer = indexer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void buildIfEmpty() {
        if (index.docCount() == 0) {
            indexer.reindexAll();
        }
    }
}
