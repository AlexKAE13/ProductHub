package ru.eltex.producthub.search.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.eltex.producthub.common.IndexedEntityChanged;

/**
 * Собирает изменения внутри транзакции и обновляет индекс после ее успешного завершения, чтобы в поиск не попали
 * данные, которые потом откатились. Вне транзакции обновляет сразу.
 */
@Component
public class SearchUpdates {

    private final SearchIndexer indexer;

    public SearchUpdates(SearchIndexer indexer) {
        this.indexer = indexer;
    }

    @EventListener
    public void changed(IndexedEntityChanged event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexer.apply(Set.of(event));
            return;
        }
        @SuppressWarnings("unchecked")
        Set<IndexedEntityChanged> pending = (Set<IndexedEntityChanged>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            Set<IndexedEntityChanged> created = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(this, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    indexer.apply(created);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(SearchUpdates.this);
                }
            });
            pending = created;
        }
        pending.add(event);
    }
}
