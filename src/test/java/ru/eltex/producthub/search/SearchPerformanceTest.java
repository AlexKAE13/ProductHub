package ru.eltex.producthub.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;

import ru.eltex.producthub.search.service.SearchIndex;
import ru.eltex.producthub.search.service.SearchIndexer;
import ru.eltex.producthub.search.service.SearchProperties;
import ru.eltex.producthub.search.service.SearchService;

/** Цель SPEC.md 8.1: ответ поиска до 100 мс на 300 SKU и 5000 артефактов (95-й перцентиль). Отдельный индекс в памяти. */
class SearchPerformanceTest {

    private static final String[] WORDS = {"настройка", "контроллер", "точка", "доступа", "wireless", "firmware", "guide", "datasheet",
            "мост", "антенна", "питание", "PoE", "Wi-Fi", "roaming", "mesh", "OLT", "GPON", "миграция", "сравнение", "презентация"};

    @Test
    void p95BelowHundredMillisOnFullSizeIndex() throws Exception {
        SearchProperties props = new SearchProperties("", 5, 20, 2, 200);
        SearchIndex index = new SearchIndex(props);
        SearchService search = new SearchService(index, props);
        Random rnd = new Random(7);
        List<Document> docs = new ArrayList<>();
        String[] lines = {"WEP", "WOP", "WLC", "LTP", "NTU", "MES", "ESR"};
        for (int i = 0; i < 300; i++) {
            String model = lines[i % lines.length] + "-" + (100 + i) + (i % 3 == 0 ? "K" : "");
            docs.add(SearchIndexer.doc("SKU", i + 1, "INTERNAL", model.replace("-", ""), model, "Точка доступа " + words(rnd, 3),
                    null, "/sku/" + model, "PRODUCTION", words(rnd, 40), null));
        }
        for (int i = 0; i < 5000; i++) {
            docs.add(SearchIndexer.doc("ARTIFACT", i + 1, i % 4 == 0 ? "EXTERNAL" : "INTERNAL", null,
                    words(rnd, 4) + " " + i, "Даташит", null, "/artifacts/" + i, "ACTUAL", words(rnd, 30), null));
        }
        index.replaceAll(docs);
        assertEquals(5300, index.docCount());

        String[] queries = {"wep-123", "wep123k", "настройка контроллера", "точка досту", "wireless guide", "миграци olt", "firmwar",
                "антена питания", "gpon datasheet", "mesh roaming"};
        for (int i = 0; i < 50; i++) {
            search.suggest(queries[i % queries.length], true);
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            long start = System.nanoTime();
            search.suggest(queries[i % queries.length], true);
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        millis.sort(null);
        long p95 = millis.get((int) (millis.size() * 0.95));
        System.out.println("Поиск на 5300 документах: p50=" + millis.get(millis.size() / 2) + " мс, p95=" + p95 + " мс, max="
                + millis.get(millis.size() - 1) + " мс");
        assertTrue(p95 < 100, "p95 = " + p95 + " мс");
        index.destroy();
    }

    private static String words(Random rnd, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(WORDS[rnd.nextInt(WORDS.length)]).append(' ');
        }
        return sb.toString().trim();
    }
}
