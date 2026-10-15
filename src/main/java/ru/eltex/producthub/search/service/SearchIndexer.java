package ru.eltex.producthub.search.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import ru.eltex.producthub.common.IndexedEntityChanged;
import ru.eltex.producthub.sku.service.ModelNormalizer;

/**
 * Собирает документы индекса из базы (SPEC.md 5.10) и обновляет индекс. Читает таблицы напрямую, одним запросом на
 * вид данных, поэтому полная пересборка быстрая и не зависит от ленивых связей JPA.
 * Видимость в поиске: контур INTERNAL или EXTERNAL записывается в документ, права проверяются при выдаче.
 */
@Component
public class SearchIndexer {

    public static final String SKU = "SKU";
    public static final String ARTIFACT = "ARTIFACT";
    public static final String FIRMWARE = "FIRMWARE";
    public static final String CASE = "CASE";
    public static final String FAQ = "FAQ";
    public static final List<String> TYPES = List.of(SKU, ARTIFACT, FIRMWARE, CASE, FAQ);

    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);
    private static final int SNIPPET = 180;

    private final JdbcTemplate jdbc;
    private final SearchIndex index;
    private final SearchProperties props;

    public SearchIndexer(JdbcTemplate jdbc, SearchIndex index, SearchProperties props) {
        this.jdbc = jdbc;
        this.index = index;
        this.props = props;
    }

    // ------------------------------------------------------------ управление индексом

    /** Полная переиндексация. Возвращает число документов. */
    public int reindexAll() {
        long start = System.nanoTime();
        List<Document> docs = new ArrayList<>();
        docs.addAll(skus(null));
        docs.addAll(artifacts(null));
        docs.addAll(firmware(null));
        docs.addAll(cases(null));
        docs.addAll(faq(null));
        index.replaceAll(docs);
        log.info("Поиск: проиндексировано {} документов за {} мс", docs.size(), (System.nanoTime() - start) / 1_000_000);
        return docs.size();
    }

    /** Обновляет документы по списку изменений. Ошибка индекса не должна ломать сохранение данных. */
    public void apply(Collection<IndexedEntityChanged> changes) {
        try {
            if (changes.size() > props.bulkThreshold()) {
                reindexAll();
                return;
            }
            Set<IndexedEntityChanged> all = new LinkedHashSet<>(changes);
            for (IndexedEntityChanged c : changes) {
                all.addAll(dependentFaq(c));
            }
            for (IndexedEntityChanged c : all) {
                reindexOne(c.type(), c.id());
            }
            index.flush();
        } catch (RuntimeException e) {
            log.error("Поиск: не удалось обновить индекс, нужна переиндексация", e);
        }
    }

    private void reindexOne(String type, Long id) {
        index.delete(type + ":" + id);
        List<Document> docs = switch (type) {
            case SKU -> skus(id);
            case ARTIFACT -> artifacts(id);
            case FIRMWARE -> firmware(id);
            case CASE -> cases(id);
            case FAQ -> faq(id);
            default -> List.of();
        };
        for (Document d : docs) {
            index.upsert(type + ":" + id, d);
        }
    }

    /** Ответы FAQ наследуют контур объекта, поэтому при его изменении их тоже нужно обновить. */
    private List<IndexedEntityChanged> dependentFaq(IndexedEntityChanged c) {
        if (!List.of(SKU, ARTIFACT, FIRMWARE).contains(c.type())) {
            return List.of();
        }
        return jdbc.queryForList("select id from question where target_type = ? and target_id = ? and publish_to_faq = true",
                Long.class, c.type(), c.id()).stream().map(q -> new IndexedEntityChanged(FAQ, q)).toList();
    }

    // ------------------------------------------------------------ SKU

    private List<Document> skus(Long id) {
        Map<Long, List<String>> tags = grouped("select st.sku_id k, t.tag_value v from sku_tag st join tag t on t.id = st.tag_id"
                + (id == null ? "" : " where st.sku_id = ?"), id);
        Map<Long, List<String>> specs = grouped("select sv.sku_id k, d.label_ru || ' ' || sv.spec_value || ' ' || coalesce(d.unit, '') v"
                + " from spec_value sv join spec_definition d on d.id = sv.definition_id"
                + (id == null ? "" : " where sv.sku_id = ?"), id);
        List<Map<String, Object>> rows = jdbc.queryForList("select s.id, s.model, s.name_ru, s.name_en, s.category, s.chip,"
                + " s.description, s.key_features, s.site_section_ru, s.site_subsection_ru, s.site_section_en,"
                + " s.site_subsection_en, s.lifecycle_status, s.contour, l.code line_code, l.name line_name"
                + " from sku s join product_line l on l.id = s.line_id where s.deleted = false"
                + (id == null ? "" : " and s.id = ?"), args(id));
        List<Document> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long sid = num(r, "id");
            String model = str(r, "model");
            String names = join(str(r, "name_ru"), str(r, "name_en"));
            String text = join(names, str(r, "category"), str(r, "chip"), str(r, "description"), str(r, "key_features"),
                    str(r, "site_section_ru"), str(r, "site_subsection_ru"), str(r, "site_section_en"),
                    str(r, "site_subsection_en"), str(r, "line_code"), str(r, "line_name"),
                    String.join(" ", tags.getOrDefault(sid, List.of())), String.join(" ", specs.getOrDefault(sid, List.of())));
            String sub = str(r, "name_ru") != null ? str(r, "name_ru") : str(r, "name_en") != null ? str(r, "name_en") : str(r, "category");
            out.add(doc(SKU, sid, str(r, "contour"), ModelNormalizer.normalize(model), model, sub,
                    snippet(str(r, "description")), "/sku/" + UriUtils.encodePath(model, StandardCharsets.UTF_8),
                    str(r, "lifecycle_status"), text, null));
        }
        return out;
    }

    // ------------------------------------------------------------ артефакты

    private List<Document> artifacts(Long id) {
        Map<Long, List<String>> tags = grouped("select at.artifact_id k, t.tag_value v from artifact_tag at join tag t on t.id = at.tag_id"
                + (id == null ? "" : " where at.artifact_id = ?"), id);
        Map<Long, List<String>> models = grouped("select a.artifact_id k, s.model v from artifact_sku a join sku s on s.id = a.sku_id"
                + (id == null ? "" : " where a.artifact_id = ?"), id);
        Map<Long, List<String>> files = grouped("select a.id k, f.file_name v from artifact a join artifact_file f on f.version_id = a.current_version_id"
                + (id == null ? "" : " where a.id = ?"), id);
        List<Map<String, Object>> rows = jdbc.queryForList("select a.id, a.title, a.description, a.language, a.status, a.contour,"
                + " t.name type_name from artifact a join artifact_type t on t.id = a.type_id where a.deleted = false"
                + (id == null ? "" : " and a.id = ?"), args(id));
        List<Document> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long aid = num(r, "id");
            List<String> m = models.getOrDefault(aid, List.of());
            String text = join(str(r, "description"), str(r, "type_name"), String.join(" ", tags.getOrDefault(aid, List.of())),
                    String.join(" ", files.getOrDefault(aid, List.of())), String.join(" ", m));
            String sub = joinWith(" · ", str(r, "type_name"), str(r, "language"),
                    m.isEmpty() ? null : String.join(", ", m.subList(0, Math.min(3, m.size()))));
            out.add(doc(ARTIFACT, aid, str(r, "contour"), null, str(r, "title"), sub, snippet(str(r, "description")),
                    "/artifacts/" + aid, str(r, "status"), text, null));
        }
        return out;
    }

    // ------------------------------------------------------------ прошивки

    private List<Document> firmware(Long id) {
        Map<Long, List<String>> models = grouped("select f.firmware_id k, s.model v from firmware_sku f join sku s on s.id = f.sku_id"
                + (id == null ? "" : " where f.firmware_id = ?"), id);
        List<Map<String, Object>> rows = jdbc.queryForList("select id, version, release_date, status, release_notes, is_public"
                + " from firmware where deleted = false" + (id == null ? "" : " and id = ?"), args(id));
        List<Document> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long fid = num(r, "id");
            List<String> m = models.getOrDefault(fid, List.of());
            String version = str(r, "version");
            String text = join(version, str(r, "release_notes"), String.join(" ", m));
            String sub = joinWith(" · ", m.isEmpty() ? null : String.join(", ", m.subList(0, Math.min(3, m.size()))),
                    str(r, "release_date"));
            out.add(doc(FIRMWARE, fid, Boolean.TRUE.equals(r.get("is_public")) ? "EXTERNAL" : "INTERNAL",
                    ModelNormalizer.normalize(version), "Прошивка " + version, sub, snippet(str(r, "release_notes")),
                    "/firmware/" + fid, str(r, "status"), text, null));
        }
        return out;
    }

    // ------------------------------------------------------------ кейсы

    private List<Document> cases(Long id) {
        Map<Long, List<String>> tags = grouped("select ct.case_id k, t.tag_value v from case_tag ct join tag t on t.id = ct.tag_id"
                + (id == null ? "" : " where ct.case_id = ?"), id);
        Map<Long, List<String>> models = grouped("select c.case_id k, s.model v from case_sku c join sku s on s.id = c.sku_id"
                + (id == null ? "" : " where c.case_id = ?"), id);
        List<Map<String, Object>> rows = jdbc.queryForList("select id, title, country, city, customer_name, customer_visible, summary,"
                + " challenge, solution, results, deployment_size, case_year, publication_allowed from case_study"
                + " where deleted = false" + (id == null ? "" : " and id = ?"), args(id));
        List<Document> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long cid = num(r, "id");
            boolean visibleCustomer = Boolean.TRUE.equals(r.get("customer_visible"));
            String text = join(str(r, "country"), str(r, "city"), str(r, "summary"), str(r, "challenge"), str(r, "solution"),
                    str(r, "results"), str(r, "deployment_size"), String.join(" ", tags.getOrDefault(cid, List.of())),
                    String.join(" ", models.getOrDefault(cid, List.of())), visibleCustomer ? str(r, "customer_name") : null);
            // Имя заказчика, если оно скрыто от внешнего контура, ищется только внутренними пользователями
            String internalText = visibleCustomer ? null : str(r, "customer_name");
            String sub = joinWith(" · ", str(r, "country"), str(r, "city"), str(r, "case_year"));
            out.add(doc(CASE, cid, Boolean.TRUE.equals(r.get("publication_allowed")) ? "EXTERNAL" : "INTERNAL", null,
                    str(r, "title"), sub, snippet(join(str(r, "summary"))), "/cases/" + cid, null, text, internalText));
        }
        return out;
    }

    // ------------------------------------------------------------ FAQ

    private List<Document> faq(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select q.id, q.question_text, q.answer, q.target_type, q.target_id,"
                + " s.model sku_model, s.contour sku_contour, s.deleted sku_deleted,"
                + " a.title art_title, a.contour art_contour, a.deleted art_deleted,"
                + " f.version fw_version, f.is_public fw_public, f.deleted fw_deleted"
                + " from question q"
                + " left join sku s on q.target_type = 'SKU' and s.id = q.target_id"
                + " left join artifact a on q.target_type = 'ARTIFACT' and a.id = q.target_id"
                + " left join firmware f on q.target_type = 'FIRMWARE' and f.id = q.target_id"
                + " where q.kind = 'QUESTION' and q.status = 'ANSWERED' and q.publish_to_faq = true"
                + (id == null ? "" : " and q.id = ?"), args(id));
        List<Document> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String type = str(r, "target_type");
            String label;
            String url;
            String contour;
            boolean gone;
            switch (type) {
                case "SKU" -> {
                    label = str(r, "sku_model");
                    url = label == null ? null : "/sku/" + UriUtils.encodePath(label, StandardCharsets.UTF_8);
                    contour = str(r, "sku_contour");
                    gone = label == null || Boolean.TRUE.equals(r.get("sku_deleted"));
                }
                case "ARTIFACT" -> {
                    label = str(r, "art_title");
                    url = "/artifacts/" + num(r, "target_id");
                    contour = str(r, "art_contour");
                    gone = label == null || Boolean.TRUE.equals(r.get("art_deleted"));
                }
                default -> {
                    label = str(r, "fw_version") == null ? null : "Прошивка " + str(r, "fw_version");
                    url = "/firmware/" + num(r, "target_id");
                    contour = Boolean.TRUE.equals(r.get("fw_public")) ? "EXTERNAL" : "INTERNAL";
                    gone = label == null || Boolean.TRUE.equals(r.get("fw_deleted"));
                }
            }
            if (gone) {
                continue;
            }
            String question = str(r, "question_text");
            out.add(doc(FAQ, num(r, "id"), contour, null, truncate(question, 200), label, snippet(str(r, "answer")), url,
                    null, join(question, str(r, "answer"), label), null));
        }
        return out;
    }

    // ------------------------------------------------------------ документ

    /** Единый вид документа: заголовок, подпись, фрагмент и адрес хранятся для выдачи, остальное только ищется. */
    public static Document doc(String type, long id, String contour, String code, String title, String sub, String snippet,
                        String url, String status, String text, String internalText) {
        Document d = new Document();
        d.add(new StringField("key", type + ":" + id, Field.Store.NO));
        d.add(new StringField("type", type, Field.Store.YES));
        d.add(new StoredField("id", id));
        d.add(new StringField("contour", contour == null ? "INTERNAL" : contour, Field.Store.NO));
        if (code != null && !code.isBlank()) {
            d.add(new StringField("code", code.toUpperCase(java.util.Locale.ROOT), Field.Store.NO));
        }
        d.add(new StoredField("title", title == null ? "" : title));
        if (sub != null) {
            d.add(new StoredField("sub", sub));
        }
        if (snippet != null) {
            d.add(new StoredField("snippet", snippet));
        }
        if (url != null) {
            d.add(new StoredField("url", url));
        }
        if (status != null) {
            d.add(new StoredField("status", status));
        }
        String all = join(title, text);
        d.add(new TextField("title_t", nz(title), Field.Store.NO));
        d.add(new TextField("title_raw", nz(title), Field.Store.NO));
        d.add(new TextField("text", all, Field.Store.NO));
        d.add(new TextField("raw", all, Field.Store.NO));
        if (internalText != null && !internalText.isBlank()) {
            d.add(new TextField("text_int", internalText, Field.Store.NO));
        }
        return d;
    }

    // ------------------------------------------------------------ мелочи

    private Map<Long, List<String>> grouped(String sql, Long id) {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            String v = rs.getString("v");
            if (v != null && !v.isBlank()) {
                map.computeIfAbsent(rs.getLong("k"), k -> new ArrayList<>()).add(v.trim());
            }
        }, args(id));
        return map;
    }

    private static Object[] args(Long id) {
        return id == null ? new Object[0] : new Object[] {id};
    }

    private static String str(Map<String, Object> r, String col) {
        Object v = r.get(col);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static long num(Map<String, Object> r, String col) {
        return ((Number) r.get(col)).longValue();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String join(String... parts) {
        return joinWith(" ", parts);
    }

    private static String joinWith(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(p.trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String snippet(String s) {
        return s == null ? null : truncate(s.replaceAll("\\s+", " ").trim(), SNIPPET);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1).trim() + "...";
    }
}
