package ru.eltex.producthub.search.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/** Обертка над индексом Lucene: запись документов и выдача поисковика. Папка или память, по настройке. */
@Component
public class SearchIndex implements DisposableBean {

    static final Analyzer ANALYZER = new PerFieldAnalyzerWrapper(HubAnalyzers.RAW, Map.of(
            "text", HubAnalyzers.STEM, "title_t", HubAnalyzers.STEM, "text_int", HubAnalyzers.STEM));

    private final Directory directory;
    private final IndexWriter writer;
    private final SearcherManager searchers;

    public SearchIndex(SearchProperties props) throws IOException {
        this.directory = props.dir() == null || props.dir().isBlank() ? new ByteBuffersDirectory()
                : FSDirectory.open(Path.of(props.dir()));
        this.writer = new IndexWriter(directory, new IndexWriterConfig(ANALYZER));
        this.searchers = new SearcherManager(writer, null);
    }

    /** Заменяет документ с этим ключом. */
    public synchronized void upsert(String key, Document doc) {
        try {
            writer.updateDocument(new Term("key", key), doc);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось обновить индекс", e);
        }
    }

    public synchronized void delete(String key) {
        try {
            writer.deleteDocuments(new Term("key", key));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось обновить индекс", e);
        }
    }

    /** Полная замена содержимого индекса. */
    public synchronized void replaceAll(List<Document> docs) {
        try {
            writer.deleteAll();
            for (Document d : docs) {
                writer.addDocument(d);
            }
            flush();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось пересобрать индекс", e);
        }
    }

    /** Фиксирует записанное и делает его видимым для поиска. */
    public synchronized void flush() {
        try {
            writer.commit();
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить индекс", e);
        }
    }

    public int docCount() {
        return use(s -> s.getIndexReader().numDocs());
    }

    public <T> T use(Function<IndexSearcher, T> action) {
        try {
            IndexSearcher searcher = searchers.acquire();
            try {
                return action.apply(searcher);
            } finally {
                searchers.release(searcher);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка поиска", e);
        }
    }

    @Override
    public void destroy() throws IOException {
        searchers.close();
        writer.close();
        directory.close();
    }
}
