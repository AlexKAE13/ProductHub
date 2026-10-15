package ru.eltex.producthub.dashboard.service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import ru.eltex.producthub.dashboard.service.Dashboard.Count;
import ru.eltex.producthub.dashboard.service.Dashboard.Item;
import ru.eltex.producthub.dashboard.service.Dashboard.Phrase;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;

/**
 * Дашборд (SPEC.md 5.12). PRODUCT видит цифры по своим SKU и материалам, HEAD и ADMIN по всем и могут выбрать продакта.
 * Все выборки агрегатные, одним запросом на цифру, поэтому открывается быстро и на 5000 материалов.
 */
@Service
public class DashboardService {

    private static final int DAYS = 30;
    private static final int LIST = 10;
    private static final int TOP = 20;
    /** SKU владельца: он основной или совладелец. */
    private static final String SKU_OWNER = " and exists (select 1 from sku_owner so where so.sku_id = %s.id and so.user_id = ?)";

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean canView(HubUserDetails user) {
        return user.getRoles().contains(Role.PRODUCT) || user.getRoles().contains(Role.HEAD) || user.getRoles().contains(Role.ADMIN);
    }

    /** Чьи цифры показывать: PRODUCT всегда свои, остальные по выбору (пусто - все). */
    public Long scopeFor(HubUserDetails user, Long requestedOwner) {
        boolean wide = user.getRoles().contains(Role.HEAD) || user.getRoles().contains(Role.ADMIN);
        return wide ? requestedOwner : user.getId();
    }

    public Dashboard build(Long owner) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusDays(DAYS));
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());

        // --- покрытие
        List<Count> byStatus = counts("select lifecycle_status k, lifecycle_status l, count(*) n from sku where deleted = false"
                + scope(SKU_OWNER.formatted("sku"), owner) + " group by lifecycle_status order by n desc", owner);
        long total = byStatus.stream().mapToLong(Count::n).sum();
        Double avg = jdbc.queryForObject("select avg(completeness) from sku where deleted = false" + scope(SKU_OWNER.formatted("sku"), owner),
                Double.class, args(owner));
        List<Item> weakest = jdbc.query("select model, name_ru, completeness from sku where deleted = false and lifecycle_status <> 'EOL'"
                        + scope(SKU_OWNER.formatted("sku"), owner) + " order by completeness asc, model limit " + LIST,
                (rs, i) -> new Item(rs.getString("model"), rs.getString("name_ru"), skuUrl(rs.getString("model")), rs.getLong("completeness")),
                args(owner));

        // --- устаревание
        long staleArtifacts = jdbc.queryForObject("select count(*) from artifact where deleted = false and review_due_at < ?"
                + scope(" and owner_id = ?", owner), Long.class, args(now, owner));
        List<Item> staleArtifactList = jdbc.query("select id, title, review_due_at from artifact where deleted = false and review_due_at < ?"
                        + scope(" and owner_id = ?", owner) + " order by review_due_at limit " + LIST,
                (rs, i) -> new Item(rs.getString("title"), rs.getTimestamp("review_due_at").toLocalDateTime().toLocalDate().toString(),
                        "/artifacts/" + rs.getLong("id"), 0), args(now, owner));
        long staleSkus = jdbc.queryForObject("select count(*) from sku where deleted = false and next_review_date < ?"
                + scope(SKU_OWNER.formatted("sku"), owner), Long.class, args(today, owner));
        List<Item> staleSkuList = jdbc.query("select model, name_ru, next_review_date from sku where deleted = false and next_review_date < ?"
                        + scope(SKU_OWNER.formatted("sku"), owner) + " order by next_review_date limit " + LIST,
                (rs, i) -> new Item(rs.getString("model"), rs.getDate("next_review_date").toString(), skuUrl(rs.getString("model")), 0),
                args(today, owner));

        // --- языки
        List<Count> byLanguage = counts("select language k, language l, count(*) n from artifact where deleted = false"
                + scope(" and owner_id = ?", owner) + " group by language order by n desc", owner);

        // --- вопросы
        long open = jdbc.queryForObject("select count(*) from question q left join sku s on q.target_type = 'SKU' and s.id = q.target_id"
                + " where q.kind = 'QUESTION' and q.status in ('NEW', 'IN_PROGRESS')" + scope(SKU_OWNER.formatted("s"), owner), Long.class, args(owner));
        List<Duration> answers = jdbc.query("select q.created_at, q.answered_at from question q"
                        + " left join sku s on q.target_type = 'SKU' and s.id = q.target_id"
                        + " where q.kind = 'QUESTION' and q.answered_at is not null and q.answered_at >= ?"
                        + scope(SKU_OWNER.formatted("s"), owner),
                (rs, i) -> Duration.between(rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("answered_at").toLocalDateTime()),
                args(since, owner));
        Double avgHours = answers.isEmpty() ? null
                : answers.stream().mapToDouble(d -> d.toMinutes() / 60.0).average().orElse(0);

        // --- задачи (у PRODUCT свои, у остальных все)
        String taskScope = scope(" and assignee_id = ?", owner);
        List<Count> tasksByStatus = counts("select status k, status l, count(*) n from task where (status <> 'DONE' or closed_at >= ?)"
                + taskScope + " group by status", owner, since);
        List<Count> byAssignee = jdbc.query("select u.id, u.full_name, count(*) n from task t join app_user u on u.id = t.assignee_id"
                        + " where t.status <> 'DONE'" + scope(" and t.assignee_id = ?", owner)
                        + " group by u.id, u.full_name order by n desc",
                (rs, i) -> new Count(String.valueOf(rs.getLong("id")), rs.getString("full_name"), rs.getLong("n")), args(owner));
        long overdue = jdbc.queryForObject("select count(*) from task where status <> 'DONE' and due_date < ?" + taskScope, Long.class,
                args(today, owner));

        // --- спрос
        List<Phrase> topQueries = jdbc.query("select lower(query_text) q, count(*) n from search_log where kind in ('PAGE', 'CLICK')"
                        + " and at >= ? group by lower(query_text) order by n desc, q limit " + TOP,
                (rs, i) -> new Phrase(rs.getString("q"), rs.getLong("n")), since);
        List<Phrase> zero = jdbc.query("select lower(query_text) q, count(*) n from search_log where kind in ('PAGE', 'SUGGEST')"
                        + " and result_count = 0 and at >= ? and length(query_text) >= 3 group by lower(query_text) order by n desc, q limit " + TOP,
                (rs, i) -> new Phrase(rs.getString("q"), rs.getLong("n")), since);
        List<Item> topSkus = jdbc.query("select s.model, s.name_ru, count(*) n from view_log v join sku s on s.id = v.entity_id"
                        + " where v.entity_type = 'SKU' and v.at >= ? and s.deleted = false"
                        + " group by s.id, s.model, s.name_ru order by n desc, s.model limit " + TOP,
                (rs, i) -> new Item(rs.getString("model"), rs.getString("name_ru"), skuUrl(rs.getString("model")), rs.getLong("n")), since);
        List<Item> topArtifacts = jdbc.query("select a.id, a.title, count(*) n from view_log v join artifact a on a.id = v.entity_id"
                        + " where v.entity_type = 'ARTIFACT' and v.at >= ? and a.deleted = false"
                        + " group by a.id, a.title order by n desc, a.title limit " + TOP,
                (rs, i) -> new Item(rs.getString("title"), null, "/artifacts/" + rs.getLong("id"), rs.getLong("n")), since);

        return new Dashboard(owner, total, byStatus, avg == null ? 0 : avg, weakest, staleArtifacts, staleArtifactList, staleSkus,
                staleSkuList, byLanguage, open, answers.size(), avgHours, tasksByStatus, byAssignee, overdue, topQueries, zero,
                topSkus, topArtifacts);
    }

    // ------------------------------------------------------------ мелочи

    private static String scope(String clause, Long owner) {
        return owner == null ? "" : clause;
    }

    /** Параметры в порядке их появления в запросе: сначала обычные, последним идет владелец (если есть). */
    private static Object[] args(Object... params) {
        List<Object> out = new ArrayList<>();
        for (Object p : params) {
            if (p != null) {
                out.add(p);
            }
        }
        return out.toArray();
    }

    private List<Count> counts(String sql, Long owner, Object... before) {
        return jdbc.query(sql, (rs, i) -> new Count(rs.getString("k"), rs.getString("l"), rs.getLong("n")), args(concat(before, owner)));
    }

    private static Object[] concat(Object[] a, Object b) {
        Object[] out = java.util.Arrays.copyOf(a, a.length + 1);
        out[a.length] = b;
        return out;
    }

    private static String skuUrl(String model) {
        return "/sku/" + UriUtils.encodePath(model, StandardCharsets.UTF_8);
    }
}
