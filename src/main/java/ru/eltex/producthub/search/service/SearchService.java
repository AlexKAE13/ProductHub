package ru.eltex.producthub.search.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.springframework.stereotype.Service;

import ru.eltex.producthub.sku.service.ModelNormalizer;

/**
 * Мгновенный поиск (SPEC.md 5.10): артикулы с нормализацией, морфология русского и английского, опечатки
 * (расстояние 1 для слов от 4 символов), группы по типам. Внешний контур видит только документы с контуром EXTERNAL.
 */
@Service
public class SearchService {

    private static final int FUZZY_MIN = 4;

    private final SearchIndex index;
    private final SearchProperties props;

    public SearchService(SearchIndex index, SearchProperties props) {
        this.index = index;
        this.props = props;
    }

    public record Hit(String type, long id, String title, String sub, String snippet, String url, String status) {
    }

    public record Group(String type, long total, List<Hit> hits) {
    }

    public record Result(String query, List<Group> groups, long millis) {

        public long total() {
            return groups.stream().mapToLong(Group::total).sum();
        }
    }

    /** Результаты для выпадающего списка: по несколько штук в каждой группе. */
    public Result suggest(String q, boolean internal) {
        long start = System.nanoTime();
        List<Group> groups = new ArrayList<>();
        Query query = build(q, internal);
        if (query != null) {
            for (String type : SearchIndexer.TYPES) {
                Group g = run(query, type, 0, props.groupLimit());
                if (g.total() > 0) {
                    groups.add(g);
                }
            }
        }
        return new Result(q, groups, (System.nanoTime() - start) / 1_000_000);
    }

    /** Одна страница результатов одного типа. */
    public Group page(String q, String type, int page, boolean internal) {
        Query query = build(q, internal);
        if (query == null) {
            return new Group(type, 0, List.of());
        }
        return run(query, type, Math.max(page, 0) * props.pageSize(), props.pageSize());
    }

    public boolean acceptable(String q) {
        return q != null && q.strip().length() >= props.minLength();
    }

    private Group run(Query base, String type, int offset, int limit) {
        Query typed = new BooleanQuery.Builder().add(base, Occur.MUST)
                .add(new TermQuery(new Term("type", type)), Occur.FILTER).build();
        return index.use(searcher -> {
            try {
                TopDocs top = searcher.search(typed, new TopScoreDocCollectorManager(offset + limit, Integer.MAX_VALUE));
                List<Hit> hits = new ArrayList<>();
                ScoreDoc[] docs = top.scoreDocs;
                for (int i = offset; i < docs.length; i++) {
                    hits.add(toHit(searcher.storedFields().document(docs[i].doc)));
                }
                return new Group(type, top.totalHits.value(), hits);
            } catch (IOException e) {
                throw new IllegalStateException("Ошибка поиска", e);
            }
        });
    }

    private static Hit toHit(Document d) {
        return new Hit(d.get("type"), d.getField("id").numericValue().longValue(), d.get("title"), d.get("sub"),
                d.get("snippet"), d.get("url"), d.get("status"));
    }

    // ------------------------------------------------------------ запрос

    /**
     * Запрос под введенный текст, либо null, если искать нечего. Если текст набран в чужой раскладке ("цуз-30д" вместо
     * "wep-30l" или "geiobdrf" вместо "прошивка"), ищется и он, и его вариант в другой раскладке.
     */
    Query build(String text, boolean internal) {
        Query main = buildOne(text, internal);
        String alt = swapLayout(text);
        if (alt == null) {
            return main;
        }
        Query other = buildOne(alt, internal);
        if (other == null) {
            return main;
        }
        if (main == null) {
            return other;
        }
        return new BooleanQuery.Builder().add(main, Occur.SHOULD).add(boost(other, 0.9f), Occur.SHOULD)
                .setMinimumNumberShouldMatch(1).build();
    }

    private static final String RU_KEYS = "йцукенгшщзхъфывапролджэячсмитьбюё";
    private static final String EN_KEYS = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";

    /**
     * Тот же текст, как если бы раскладка была другой: русские буквы становятся латинскими на тех же клавишах и
     * наоборот. Цифры и дефисы не меняются. Пусто, если менять нечего. Латиница с цифрами (артикулы) не переводится.
     */
    public static String swapLayout(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip().toLowerCase();
        boolean cyrillic = t.chars().anyMatch(c -> RU_KEYS.indexOf(c) >= 0);
        boolean digits = t.chars().anyMatch(Character::isDigit);
        boolean latin = t.chars().anyMatch(c -> c >= 'a' && c <= 'z');
        StringBuilder out = new StringBuilder();
        if (cyrillic && !latin) {
            for (char c : t.toCharArray()) {
                int i = RU_KEYS.indexOf(c);
                out.append(i >= 0 ? EN_KEYS.charAt(i) : c);
            }
        } else if (latin && !cyrillic && !digits) {
            for (char c : t.toCharArray()) {
                int i = EN_KEYS.indexOf(c);
                out.append(i >= 0 && Character.isLetter(RU_KEYS.charAt(i)) ? RU_KEYS.charAt(i) : c);
            }
        } else {
            return null;
        }
        String result = out.toString();
        return result.equals(t) ? null : result;
    }

    private Query buildOne(String text, boolean internal) {
        if (!acceptable(text)) {
            return null;
        }
        String q = text.strip();
        List<String> raw = tokens(HubAnalyzers.RAW, q);
        List<String> stem = tokens(HubAnalyzers.STEM, q);
        if (raw.isEmpty() || raw.size() != stem.size()) {
            return null;
        }
        BooleanQuery.Builder words = new BooleanQuery.Builder();
        for (int i = 0; i < raw.size(); i++) {
            String r = raw.get(i);
            String s = stem.get(i);
            boolean last = i == raw.size() - 1;
            BooleanQuery.Builder one = new BooleanQuery.Builder();
            one.add(boost(new TermQuery(new Term("title_t", s)), 6), Occur.SHOULD);
            one.add(boost(new TermQuery(new Term("text", s)), 2), Occur.SHOULD);
            if (internal) {
                one.add(boost(new TermQuery(new Term("text_int", s)), 2), Occur.SHOULD);
            }
            if (last) {
                // Последнее слово еще печатается: подходят любые слова, которые с него начинаются
                one.add(boost(new PrefixQuery(new Term("title_raw", r)), 3), Occur.SHOULD);
                one.add(new PrefixQuery(new Term("raw", r)), Occur.SHOULD);
            }
            if (r.length() >= FUZZY_MIN) {
                one.add(boost(new FuzzyQuery(new Term("title_raw", r), 1, 1), 1.5f), Occur.SHOULD);
                one.add(boost(new FuzzyQuery(new Term("raw", r), 1, 1), 0.4f), Occur.SHOULD);
            }
            words.add(one.build(), Occur.MUST);
        }
        String code = ModelNormalizer.normalize(q);
        BooleanQuery.Builder top = new BooleanQuery.Builder();
        // Точное совпадение артикула всегда первое: "wep550k", "WEP 550K" и "wep-550k" дают один код
        top.add(boost(new TermQuery(new Term("code", code)), 100), Occur.SHOULD);
        if (code.length() >= 3) {
            top.add(boost(new PrefixQuery(new Term("code", code)), 15), Occur.SHOULD);
        }
        top.add(words.build(), Occur.SHOULD);
        top.setMinimumNumberShouldMatch(1);
        BooleanQuery.Builder result = new BooleanQuery.Builder().add(top.build(), Occur.MUST);
        if (!internal) {
            result.add(new TermQuery(new Term("contour", "EXTERNAL")), Occur.FILTER);
        }
        return result.build();
    }

    private static Query boost(Query q, float boost) {
        return new BoostQuery(q, boost);
    }

    private static List<String> tokens(org.apache.lucene.analysis.Analyzer analyzer, String text) {
        List<String> out = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("text", text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                out.add(term.toString());
            }
            ts.end();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
